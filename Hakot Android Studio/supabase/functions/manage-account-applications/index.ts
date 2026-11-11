import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const ALLOWED_ROLES = ['municipal_admin', 'municipal_staff', 'driver']

async function findAllUsers(supabaseAdmin: any) {
  let allUsers: any[] = []
  let page = 1
  const perPage = 1000
  while (true) {
    const { data, error } = await supabaseAdmin.auth.admin.listUsers({ page, perPage })
    if (error) {
      throw new Error(`Failed to list auth users during pagination: ${error.message}`)
    }
    if (!data || !data.users || !Array.isArray(data.users)) {
      throw new Error('Invalid or missing users array in Auth API response')
    }
    allUsers = allUsers.concat(data.users)
    if (data.users.length < perPage) {
      break
    }
    page++
  }
  return allUsers
}

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

    if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceKey) {
      const missing = []
      if (!supabaseUrl) missing.push('SUPABASE_URL')
      if (!supabaseAnonKey) missing.push('SUPABASE_ANON_KEY')
      if (!supabaseServiceKey) missing.push('SUPABASE_SERVICE_ROLE_KEY')
      console.error(`[manage-account-applications] HTTP 500: Missing environment variables: ${missing.join(', ')}`)
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
        .select('id, full_name, email, contact_number, municipality_id, company_name, office_department, official_position, requested_role, status, submitted_at, reviewed_at, reviewed_by, review_notes')
        .eq('status', 'PENDING')
        .order('submitted_at', { ascending: false })

      if (error) {
        console.error(`[manage-account-applications] HTTP 500: LIST_PENDING query failed: ${error.message}`)
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

      // 1. Acquire atomic approval lock via claim_approval_lock RPC
      const { data: lockResult, error: lockError } = await supabaseAdmin.rpc('claim_approval_lock', {
        p_app_id: applicationId,
        p_admin_id: user.id,
      })

      if (lockError || !lockResult || lockResult.length === 0 || !lockResult[0].success) {
        const errReason = lockResult?.[0]?.error_message || lockError?.message || 'Failed to acquire approval lock.'
        return new Response(JSON.stringify({ error: `Conflict: ${errReason}` }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const ownerToken = lockResult[0].owner_token

      // Helper function for pre-invitation failure cleanup ONLY
      async function failClaimPreInvitation(errorMsg: string) {
        try {
          await supabaseAdmin.rpc('release_or_fail_claim', {
            p_app_id: applicationId,
            p_owner_token: ownerToken,
            p_error_message: errorMsg,
          })
        } catch (_) {}
      }

      // 2. Fetch application record
      const { data: targetApp, error: fetchAppError } = await supabaseAdmin
        .from('account_applications')
        .select('*')
        .eq('id', applicationId)
        .single()

      if (fetchAppError || !targetApp) {
        await failClaimPreInvitation('Application not found after lock acquisition.')
        return new Response(JSON.stringify({ error: 'Conflict: Application not found.' }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 3. Validate role allowlist
      const requestedRole = (targetApp.requested_role || '').toLowerCase().trim()
      if (!ALLOWED_ROLES.includes(requestedRole) || requestedRole === 'system_owner') {
        await failClaimPreInvitation(`Forbidden role: ${requestedRole}`)
        return new Response(JSON.stringify({ error: `Forbidden: Requested role '${requestedRole}' is not permitted for public applications.` }), {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 4. Validate municipal scope for municipal roles
      if ((requestedRole === 'municipal_admin' || requestedRole === 'municipal_staff') && !targetApp.municipality_id) {
        await failClaimPreInvitation('Missing municipality scope ID for municipal role.')
        return new Response(JSON.stringify({ error: 'Validation failed: Municipality scope ID is required for municipal roles.' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      if (requestedRole === 'district_admin' || requestedRole === 'company_admin') {
        await failClaimPreInvitation(`Role ${requestedRole} provisioning not supported in initial implementation.`)
        return new Response(JSON.stringify({ error: `Validation failed: ${requestedRole} approval is currently excluded from automated provisioning.` }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 5. Paginated duplicate-user lookup with strict error propagation and normalization
      let allUsers: any[]
      try {
        allUsers = await findAllUsers(supabaseAdmin)
      } catch (listErr: any) {
        const msg = listErr instanceof Error ? listErr.message : String(listErr)
        console.error(`[manage-account-applications] HTTP 500: Failed to list auth users: ${msg}`)
        await failClaimPreInvitation(`Failed to list auth users: ${msg}`)
        return new Response(JSON.stringify({ error: `Server error listing users: ${msg}` }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const normalizedEmail = (targetApp.email || '').trim().toLowerCase()
      let existingAuthUser = allUsers.find(u => u.user_metadata?.application_id === applicationId)

      if (!existingAuthUser) {
        const emailConflictUser = allUsers.find(u => (u.email || '').trim().toLowerCase() === normalizedEmail)
        if (emailConflictUser) {
          await failClaimPreInvitation('Email conflict with existing Auth user.')
          return new Response(JSON.stringify({ error: 'Conflict: A user account already exists in Supabase Auth with this email address under a different identity.' }), {
            status: 409,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      }

      let userId: string

      // 6. Auth Invitation / Retrieval (Post-invitation boundary: CLAIMED status must be retained permanently from this point forward)
      if (existingAuthUser) {
        userId = existingAuthUser.id
      } else {
        const { data: authData, error: authInviteError } = await supabaseAdmin.auth.admin.inviteUserByEmail(targetApp.email, {
          data: { full_name: targetApp.full_name, application_id: applicationId },
        })

        if (authInviteError || !authData.user) {
          console.error(`[manage-account-applications] HTTP 500: Auth invite failed: ${authInviteError?.message}`)
          return new Response(JSON.stringify({ error: `Failed to create auth user: ${authInviteError?.message}. Claim retained for audit and retry.` }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
        userId = authData.user.id
      }

      // 7. Profile verification and creation (Retaining CLAIMED on failure)
      const { data: existingProfile, error: profileFetchError } = await supabaseAdmin
        .from('profiles')
        .select('role, account_status, municipality_id')
        .eq('id', userId)
        .maybeSingle()

      if (profileFetchError) {
        console.error(`[manage-account-applications] HTTP 500: Profile fetch failed: ${profileFetchError.message}`)
        return new Response(JSON.stringify({ error: `Failed to query existing profile: ${profileFetchError.message}. Claim retained for audit and retry.` }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      if (existingProfile) {
        if (existingProfile.role !== requestedRole || existingProfile.account_status !== 'active' || (targetApp.municipality_id && existingProfile.municipality_id !== targetApp.municipality_id)) {
          return new Response(JSON.stringify({ error: 'Conflict: An existing profile is associated with this user but has conflicting role, status, or organizational scope. Claim retained.' }), {
            status: 409,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      } else {
        const profilePayload: Record<string, any> = {
          id: userId,
          full_name: targetApp.full_name,
          role: requestedRole,
          account_status: 'active',
          municipality_id: targetApp.municipality_id || null,
        }

        const { error: profileCreateError } = await supabaseAdmin
          .from('profiles')
          .insert(profilePayload)

        if (profileCreateError) {
          console.error(`[manage-account-applications] HTTP 500: Profile insert failed: ${profileCreateError.message}`)
          return new Response(JSON.stringify({ error: `Failed to create profile: ${profileCreateError.message}. Claim retained; do not re-invite.` }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      }

      // 8. Finalize approval via finalize_approval_claim RPC (Checking boolean result explicitly)
      const { data: finalizeResult, error: finalizeError } = await supabaseAdmin.rpc('finalize_approval_claim', {
        p_app_id: applicationId,
        p_owner_token: ownerToken,
        p_admin_id: user.id,
      })

      if (finalizeError || !finalizeResult) {
        console.error(`[manage-account-applications] HTTP 500: Claim finalization failed: ${finalizeError?.message || 'RPC returned false'}`)
        return new Response(JSON.stringify({ error: `Account provisioned successfully (Auth user & profile verified), but claim finalization returned false: ${finalizeError?.message || 'RPC returned false'}. Note: Account is provisioned; claim remains locked for manual reconciliation.` }), {
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

      const { data: rejectResult, error: rejectError } = await supabaseAdmin.rpc('reject_application_safely', {
        p_app_id: applicationId,
        p_admin_id: user.id,
      })

      if (rejectError || !rejectResult) {
        return new Response(JSON.stringify({ error: 'Conflict or not found: Application is already processed, missing, or currently locked by an active approval operation.' }), {
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
    const errorMessage = err instanceof Error ? err.message : 'Internal server error'
    const errorStack = err instanceof Error ? err.stack : undefined
    console.error(`[manage-account-applications] HTTP 500: Unhandled exception: ${errorMessage}`, errorStack)
    return new Response(JSON.stringify({ error: errorMessage }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
