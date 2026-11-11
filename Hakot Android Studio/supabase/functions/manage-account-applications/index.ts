import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const ALLOWED_ROLES = ['municipal_admin', 'municipal_staff', 'company_admin', 'district_admin', 'driver']

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Method not allowed. Use POST.' }), {
      status: 405,
      headers: { ...corsHeaders, 'Content-Type': 'application/json', 'Allow': 'POST, OPTIONS' },
    })
  }

  try {
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Missing Authorization header' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? ''
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(JSON.stringify({ error: 'Server configuration error' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Client with caller's JWT for auth verification
    const supabaseClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    })

    const { data: { user }, error: userError } = await supabaseClient.auth.getUser()
    if (userError || !user) {
      return new Response(JSON.stringify({ error: 'Unauthorized: Invalid or expired token' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Verify caller is active system_owner (System Owner platform-wide review only)
    const { data: profile, error: profileError } = await supabaseClient
      .from('profiles')
      .select('role, account_status')
      .eq('id', user.id)
      .single()

    if (profileError || !profile || profile.role !== 'system_owner' || profile.account_status !== 'active') {
      return new Response(JSON.stringify({ error: 'Forbidden: Platform System Owner access required' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    let body
    try {
      body = await req.json()
    } catch (_) {
      return new Response(JSON.stringify({ error: 'Invalid JSON request body' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const { action, applicationId } = body
    if (!action || typeof action !== 'string') {
      return new Response(JSON.stringify({ error: 'Missing or invalid action parameter' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Admin client using service role key for privileged operations
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey)

    if (action === 'LIST_PENDING') {
      const { data, error } = await supabaseAdmin
        .from('account_applications')
        .select('id, full_name, email, contact_number, municipality_id, office_department, official_position, requested_role, status, submitted_at, reviewed_at, reviewed_by, review_notes')
        .eq('status', 'PENDING')
        .order('submitted_at', { ascending: false })

      if (error) {
        return new Response(JSON.stringify({ error: error.message }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      return new Response(JSON.stringify({ data }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    if (action === 'APPROVE') {
      if (!applicationId || typeof applicationId !== 'string') {
        return new Response(JSON.stringify({ error: 'Missing or invalid applicationId' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // Fetch application record
      const { data: targetApp, error: fetchAppError } = await supabaseAdmin
        .from('account_applications')
        .select('*')
        .eq('id', applicationId)
        .eq('status', 'PENDING')
        .single()

      if (fetchAppError || !targetApp) {
        return new Response(JSON.stringify({ error: 'Conflict: Application not found, already processed, or not pending.' }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const requestedRole = (targetApp.requested_role || '').toLowerCase().trim()
      if (!ALLOWED_ROLES.includes(requestedRole) || requestedRole === 'system_owner') {
        return new Response(JSON.stringify({ error: `Forbidden: Requested role '${requestedRole}' is not permitted for public applications.` }), {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // Provisioning is blocked pending authorized schema/workflow migration design
      return new Response(JSON.stringify({ error: 'Account provisioning is currently blocked pending authorized schema and workflow migration design.' }), {
        status: 501,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    if (action === 'REJECT') {
      if (!applicationId || typeof applicationId !== 'string') {
        return new Response(JSON.stringify({ error: 'Missing or invalid applicationId' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const { data: updatedApps, error: updateError } = await supabaseAdmin
        .from('account_applications')
        .update({
          status: 'REJECTED',
          reviewed_at: new Date().toISOString(),
          reviewed_by: user.id,
        })
        .eq('id', applicationId)
        .eq('status', 'PENDING')
        .select()

      if (updateError || !updatedApps || updatedApps.length === 0) {
        return new Response(JSON.stringify({ error: 'Conflict or not found: Application is already processed, missing, or not pending.' }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      return new Response(JSON.stringify({ success: true, message: 'Application rejected successfully.' }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    return new Response(JSON.stringify({ error: 'Invalid action parameter' }), {
      status: 400,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message ?? 'Internal server error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
