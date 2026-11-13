import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

Deno.serve(async (req) => {
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
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''

    if (!supabaseUrl || !supabaseServiceKey) {
      console.error('[submit-organization-application] Missing server environment variables')
      return new Response(JSON.stringify({ error: 'Server configuration error' }), {
        status: 500,
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

    const {
      organizationType,
      organizationName,
      email,
      repFullName,
      repPosition,
      contactNumber,
      municipalityId,
      requestedMunicipalities,
      certDocumentUrl,
      officeDepartment,
      officialPosition
    } = body

    const cleanOrgType = (organizationType || '').toString().toUpperCase().trim()
    if (cleanOrgType !== 'LGU' && cleanOrgType !== 'PROVIDER') {
      return new Response(JSON.stringify({ error: 'Validation failed: organizationType must be either LGU or PROVIDER.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const cleanOrgName = (organizationName || '').toString().trim()
    if (!cleanOrgName || cleanOrgName.length > 150) {
      return new Response(JSON.stringify({ error: 'Validation failed: Organization name is required (max 150 characters).' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const cleanEmail = (email || '').toString().trim().toLowerCase()
    if (!EMAIL_REGEX.test(cleanEmail) || cleanEmail.length > 254) {
      return new Response(JSON.stringify({ error: 'Validation failed: A valid email address is required.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const cleanRepName = (repFullName || '').toString().trim()
    if (!cleanRepName || cleanRepName.length > 100) {
      return new Response(JSON.stringify({ error: 'Validation failed: Representative full name is required.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const cleanContact = (contactNumber || '').toString().trim()
    if (!cleanContact || cleanContact.length > 50) {
      return new Response(JSON.stringify({ error: 'Validation failed: Contact number is required.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const primaryMuniId = (municipalityId || '').toString().trim()
    if (!primaryMuniId) {
      return new Response(JSON.stringify({ error: 'Validation failed: Primary municipality ID is required.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const muniIdsList: string[] = []
    muniIdsList.push(primaryMuniId)

    if (Array.isArray(requestedMunicipalities)) {
      for (const mId of requestedMunicipalities) {
        const cleanMId = (mId || '').toString().trim()
        if (cleanMId && !muniIdsList.includes(cleanMId)) {
          muniIdsList.push(cleanMId)
        }
      }
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey)

    // Call atomic transactional RPC submit_organization_application
    const { data: rpcData, error: rpcError } = await supabaseAdmin.rpc('submit_organization_application', {
      p_organization_type: cleanOrgType,
      p_organization_name: cleanOrgName,
      p_email: cleanEmail,
      p_rep_full_name: cleanRepName,
      p_rep_position: (repPosition || officialPosition || 'Organization Administrator').toString().trim(),
      p_contact_number: cleanContact,
      p_municipality_id: primaryMuniId,
      p_requested_municipalities: muniIdsList,
      p_cert_document_url: (certDocumentUrl || '').toString().trim(),
      p_office_department: (officeDepartment || (cleanOrgType === 'LGU' ? 'Municipal Sanitation Office' : 'Operations Department')).toString().trim()
    })

    if (rpcError || !rpcData) {
      const errMessage = rpcError?.message || 'Failed to submit application.'
      console.error(`[submit-organization-application] RPC failed: ${errMessage}`)
      return new Response(JSON.stringify({ error: errMessage }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const applicationId = rpcData

    // Write audit log entry (safely handling errors without exposing internal DB details)
    try {
      await supabaseAdmin.from('audit_logs').insert({
        action: 'SUBMIT_ORGANIZATION_APPLICATION',
        table_name: 'account_applications',
        record_id: applicationId,
        details: { organization_name: cleanOrgName, organization_type: cleanOrgType, email: cleanEmail }
      })
    } catch (_) {}

    return new Response(JSON.stringify({ success: true, applicationId, message: 'Organization application submitted successfully.' }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : 'Internal server error'
    console.error(`[submit-organization-application] Unhandled exception: ${errorMessage}`)
    return new Response(JSON.stringify({ error: 'Internal server error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
