import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const ALLOWED_ROLES = ['municipal_admin', 'municipal_staff', 'driver', 'district_admin']

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

    // Verify caller is active system_owner
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
        .select('id, full_name, email, contact_number, municipality_id, office_department, official_position, requested_role, status, created_at')
        .eq('status', 'PENDING')
        .order('created_at', { ascending: false })

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

      // 1. Atomic claim: transition application status from PENDING to PROCESSING
      const { data: claimedApp, error: claimError } = await supabaseAdmin
        .from('account_applications')
        .update({ status: 'PROCESSING' })
        .eq('id', applicationId)
        .eq('status', 'PENDING')
        .select()
        .single()

      if (claimError || !claimedApp) {
        const { data: existingApp } = await supabaseAdmin
          .from('account_applications')
          .select('status')
          .eq('id', applicationId)
          .single()

        if (existingApp?.status === 'APPROVED') {
          return new Response(JSON.stringify({ error: 'Conflict: Application has already been approved.' }), {
            status: 409,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
        if (existingApp?.status === 'PROCESSING') {
          return new Response(JSON.stringify({ error: 'Conflict: Application is currently being processed by another admin.' }), {
            status: 409,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
        return new Response(JSON.stringify({ error: 'Conflict: Application not found or no longer pending.' }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 2. Strict role allowlist validation
      const requestedRole = (claimedApp.requested_role || '').toLowerCase().trim()
      if (!ALLOWED_ROLES.includes(requestedRole)) {
        await supabaseAdmin.from('account_applications').update({ status: 'PENDING' }).eq('id', applicationId)
        return new Response(JSON.stringify({ error: `Forbidden: Requested role '${requestedRole}' is not permitted for public applications.` }), {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      if (requestedRole !== 'driver' && !claimedApp.municipality_id) {
        await supabaseAdmin.from('account_applications').update({ status: 'PENDING' }).eq('id', applicationId)
        return new Response(JSON.stringify({ error: 'Validation failed: Municipality scope ID is required for administrative roles.' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 3. Check if Auth user already exists for this email
      let userId: string | null = null
      const { data: listUsersData } = await supabaseAdmin.auth.admin.listUsers()
      const existingUser = listUsersData?.users?.find(u => u.email?.toLowerCase() === claimedApp.email.toLowerCase())

      if (existingUser) {
        userId = existingUser.id
      } else {
        const { data: authData, error: authInviteError } = await supabaseAdmin.auth.admin.inviteUserByEmail(claimedApp.email, {
          data: { full_name: claimedApp.full_name },
        })

        if (authInviteError || !authData.user) {
          await supabaseAdmin.from('account_applications').update({ status: 'PENDING' }).eq('id', applicationId)
          return new Response(JSON.stringify({ error: `Failed to invite user: ${authInviteError?.message}` }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
        userId = authData.user.id
      }

      // 4. Create or verify profile
      const { data: existingProfile } = await supabaseAdmin
        .from('profiles')
        .select('id')
        .eq('id', userId)
        .single()

      if (!existingProfile) {
        const { error: profileCreateError } = await supabaseAdmin
          .from('profiles')
          .insert({
            id: userId,
            full_name: claimedApp.full_name,
            email: claimedApp.email,
            role: requestedRole,
            account_status: 'active',
            municipality_id: claimedApp.municipality_id,
            office_department: claimedApp.office_department,
            official_position: claimedApp.official_position,
          })

        if (profileCreateError) {
          if (!existingUser) {
            try { await supabaseAdmin.auth.admin.deleteUser(userId) } catch (_) {}
          }
          await supabaseAdmin.from('account_applications').update({ status: 'PENDING' }).eq('id', applicationId)
          return new Response(JSON.stringify({ error: `Failed to create profile: ${profileCreateError.message}` }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      }

      // 5. Finalize application status to APPROVED
      const { data: finalUpdate, error: updateError } = await supabaseAdmin
        .from('account_applications')
        .update({ status: 'APPROVED' })
        .eq('id', applicationId)
        .eq('status', 'PROCESSING')
        .select()

      if (updateError || !finalUpdate || finalUpdate.length === 0) {
        return new Response(JSON.stringify({ error: `Account provisioned successfully, but final status update failed: ${updateError?.message || 'Zero records updated'}` }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      return new Response(JSON.stringify({ success: true, message: 'Account invitation sent and application approved successfully.' }), {
        status: 200,
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
        .update({ status: 'REJECTED' })
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
