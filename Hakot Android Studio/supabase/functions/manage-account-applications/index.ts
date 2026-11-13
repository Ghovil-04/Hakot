import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const ALLOWED_ROLES = ['municipal_admin', 'municipal_staff', 'driver', 'company_admin']

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

async function computeHmacSha256(secret: string, message: string): Promise<string> {
  const enc = new TextEncoder()
  const keyData = enc.encode(secret)
  const msgData = enc.encode(message)

  const key = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  )

  const signature = await crypto.subtle.sign("HMAC", key, msgData)
  const hashArray = Array.from(new Uint8Array(signature))
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
}

function generateSecureSixDigitCode(): string {
  const array = new Uint32Array(1)
  crypto.getRandomValues(array)
  const num = (array[0] % 900000) + 100000
  return num.toString()
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
    const otpSecret = Deno.env.get('OTP_HMAC_SECRET') ?? ''

    if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceKey || !otpSecret) {
      const missing = []
      if (!supabaseUrl) missing.push('SUPABASE_URL')
      if (!supabaseAnonKey) missing.push('SUPABASE_ANON_KEY')
      if (!supabaseServiceKey) missing.push('SUPABASE_SERVICE_ROLE_KEY')
      if (!otpSecret) missing.push('OTP_HMAC_SECRET')
      console.error(`[manage-account-applications] HTTP 500: Missing environment variables: ${missing.join(', ')}`)
      return new Response(JSON.stringify({ error: 'Server configuration error' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

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

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey)

    if (action === 'LIST_PENDING') {
      const { data, error } = await supabaseAdmin
        .from('account_applications')
        .select('id, full_name, email, contact_number, municipality_id, company_name, office_department, official_position, requested_role, status, submitted_at, reviewed_at, reviewed_by, review_notes, organization_type, organization_name, rep_full_name, rep_position, rep_contact_number, cert_document_url, invitation_state')
        .eq('status', 'PENDING')
        .order('submitted_at', { ascending: false })

      if (error) {
        console.error(`[manage-account-applications] HTTP 500: LIST_PENDING query failed: ${error.message}`)
        return new Response(JSON.stringify({ error: 'Server error: Failed to retrieve pending applications.' }), {
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

      // 1. Acquire approval claim
      const { data: lockResult, error: lockError } = await supabaseAdmin.rpc('claim_approval_lock', {
        p_app_id: applicationId,
        p_admin_id: user.id,
      })

      if (lockError || !lockResult || lockResult.length === 0 || !lockResult[0].success) {
        const errReason = lockResult?.[0]?.error_message || lockError?.message || 'Failed to acquire approval lock.'

        // Re-issuance path for an already APPROVED & PROVISIONED application
        const { data: approvedApp } = await supabaseAdmin
          .from('account_applications')
          .select('id, full_name, email, status, organization_id, auth_user_id')
          .eq('id', applicationId)
          .single()

        if (approvedApp && approvedApp.status === 'APPROVED' && approvedApp.organization_id && approvedApp.auth_user_id) {
          const { data: authUserObj, error: authUserErr } = await supabaseAdmin.auth.admin.getUserById(approvedApp.auth_user_id)
          if (authUserErr || !authUserObj.user) {
            return new Response(JSON.stringify({ error: 'Conflict: Linked Auth user for approved application not found.' }), {
              status: 409,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            })
          }

          const normalizedAppEmail = (approvedApp.email || '').trim().toLowerCase()
          const normalizedAuthEmail = (authUserObj.user.email || '').trim().toLowerCase()
          if (normalizedAuthEmail !== normalizedAppEmail) {
            return new Response(JSON.stringify({ error: 'Conflict: Approved Auth user email does not match application.' }), {
              status: 409,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            })
          }

          const rawCode = generateSecureSixDigitCode()
          const codeHash = await computeHmacSha256(otpSecret, rawCode)

          const { data: otpIdData, error: otpError } = await supabaseAdmin.rpc('issue_account_otp', {
            p_application_id: applicationId,
            p_user_id: null,
            p_email: approvedApp.email,
            p_code_hash: codeHash,
            p_code_type: 'ACTIVATION',
            p_ttl_minutes: 60
          })

          if (otpError || !otpIdData) {
            return new Response(JSON.stringify({ error: 'Failed to generate activation OTP code for approved application.' }), {
              status: 500,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            })
          }

          const { error: auditErr } = await supabaseAdmin.from('audit_logs').insert({
            actor_id: user.id,
            action: 'REISSUE_ACTIVATION_CODE',
            table_name: 'account_applications',
            record_id: applicationId,
            details: { organization_id: approvedApp.organization_id }
          })

          if (auditErr) {
            console.error(`[manage-account-applications] Audit log insert failed on re-issuance: ${auditErr.message}`)
          }

          return new Response(JSON.stringify({
            success: true,
            message: 'Fresh activation code generated for approved application.',
            activationCode: rawCode
          }), {
            status: 200,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }

        console.error(`[manage-account-applications] Claim acquisition failed: ${errReason}`)
        return new Response(JSON.stringify({ error: `Conflict: ${errReason}` }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const ownerToken = lockResult[0].owner_token

      async function failClaimPreFinalization(errorMsg: string): Promise<boolean> {
        try {
          const { data: releaseData, error: releaseErr } = await supabaseAdmin.rpc('release_or_fail_claim', {
            p_app_id: applicationId,
            p_owner_token: ownerToken,
            p_error_message: errorMsg,
          })
          if (releaseErr || releaseData !== true) {
            console.error(`[manage-account-applications] Failed to release/fail claim for app ${applicationId}: ${releaseErr?.message || 'RPC returned non-true value'}`)
            return false
          }
          return true
        } catch (e) {
          console.error(`[manage-account-applications] Exception in failClaimPreFinalization: ${e instanceof Error ? e.message : String(e)}`)
          return false
        }
      }

      // 2. Validate the pending application and organization type
      const { data: targetApp, error: fetchAppError } = await supabaseAdmin
        .from('account_applications')
        .select('id, full_name, email, contact_number, municipality_id, company_name, office_department, official_position, requested_role, status, organization_type, organization_name, rep_full_name, rep_position, rep_contact_number, cert_document_url, invitation_state, auth_user_id')
        .eq('id', applicationId)
        .single()

      if (fetchAppError || !targetApp) {
        await failClaimPreFinalization('Application not found after lock acquisition.')
        return new Response(JSON.stringify({ error: 'Conflict: Application not found.' }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      if (targetApp.status !== 'PENDING') {
        const msg = `Conflict: Application status is '${targetApp.status}', not PENDING.`
        await failClaimPreFinalization(msg)
        return new Response(JSON.stringify({ error: msg }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const orgType = (targetApp.organization_type || '').toUpperCase().trim()
      if (orgType !== 'LGU' && orgType !== 'PROVIDER') {
        const errMsg = `Validation failed: Invalid organization type '${targetApp.organization_type}'. Must be LGU or PROVIDER.`
        await failClaimPreFinalization(errMsg)
        return new Response(JSON.stringify({ error: errMsg }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const derivedRole = orgType === 'PROVIDER' ? 'company_admin' : 'municipal_admin'

      if (!ALLOWED_ROLES.includes(derivedRole)) {
        await failClaimPreFinalization(`Forbidden role: ${derivedRole}`)
        return new Response(JSON.stringify({ error: 'Forbidden: Derived role is not permitted.' }), {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      if (!targetApp.municipality_id) {
        await failClaimPreFinalization('Missing municipality scope ID.')
        return new Response(JSON.stringify({ error: 'Validation failed: Municipality scope ID is required.' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const inviteState = targetApp.invitation_state || 'NOT_ATTEMPTED'
      if (inviteState !== 'NOT_ATTEMPTED' && inviteState !== 'ATTEMPT_IN_PROGRESS' && inviteState !== 'INVITATION_CONFIRMED' && inviteState !== 'OUTCOME_UNKNOWN') {
        const msg = `Conflict: Application has unhandled invitation state '${inviteState}'. Manual reconciliation required.`
        await failClaimPreFinalization(msg)
        return new Response(JSON.stringify({ error: msg }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 3. Provision or recover the organization
      const { data: orgIdData, error: orgRpcError } = await supabaseAdmin.rpc('provision_organization_for_application', {
        p_app_id: applicationId,
      })

      if (orgRpcError || !orgIdData) {
        const msg = orgRpcError?.message || 'Failed to provision organization.'
        await failClaimPreFinalization(`Organization provisioning failed: ${msg}`)
        return new Response(JSON.stringify({ error: 'Server error: Failed to provision organization.' }), {
          status: orgRpcError?.message?.includes('Validation failed') ? 400 : 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const organizationId = orgIdData

      // 4. Find or create the Auth user; verify normalized email and user_metadata.application_id
      let userId: string | null = null
      const normalizedAppEmail = (targetApp.email || '').trim().toLowerCase()

      if (targetApp.auth_user_id) {
        const { data: authUserObj, error: authUserErr } = await supabaseAdmin.auth.admin.getUserById(targetApp.auth_user_id)
        if (authUserErr || !authUserObj.user) {
          const msg = 'Conflict: Populated auth_user_id does not correspond to an existing Auth user.'
          await failClaimPreFinalization(msg)
          return new Response(JSON.stringify({ error: msg }), {
            status: 409,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
        const authUserEmail = (authUserObj.user.email || '').trim().toLowerCase()
        if (authUserEmail !== normalizedAppEmail) {
          const msg = 'Conflict: Auth user email does not match application email.'
          await failClaimPreFinalization(msg)
          return new Response(JSON.stringify({ error: msg }), {
            status: 409,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
        userId = targetApp.auth_user_id
      } else {
        let allUsers: any[]
        try {
          allUsers = await findAllUsers(supabaseAdmin)
        } catch (listErr: any) {
          const msg = listErr instanceof Error ? listErr.message : String(listErr)
          await failClaimPreFinalization(`Failed to list auth users: ${msg}`)
          return new Response(JSON.stringify({ error: 'Server error: Failed to list auth users.' }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }

        const emailConflictUser = allUsers.find(u => (u.email || '').trim().toLowerCase() === normalizedAppEmail)

        if (emailConflictUser) {
          const appMetaId = emailConflictUser.user_metadata?.application_id
          if (appMetaId === applicationId) {
            userId = emailConflictUser.id
          } else {
            const msg = 'Conflict: A user account already exists in Supabase Auth with this email address for another application.'
            await failClaimPreFinalization(msg)
            return new Response(JSON.stringify({ error: msg }), {
              status: 409,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            })
          }
        } else {
          const tempPassword = crypto.randomUUID() + 'A1!'
          const { data: createData, error: createError } = await supabaseAdmin.auth.admin.createUser({
            email: targetApp.email,
            password: tempPassword,
            email_confirm: true,
            user_metadata: { full_name: targetApp.full_name, application_id: applicationId }
          })

          if (createError || !createData.user) {
            const errMsgStr = createError?.message || 'Failed to create user in Auth'
            await failClaimPreFinalization(`Auth user creation failed: ${errMsgStr}`)
            return new Response(JSON.stringify({ error: `Failed to create user in Auth: ${errMsgStr}` }), {
              status: 500,
              headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            })
          }

          userId = createData.user.id
        }
      }

      if (!userId) {
        await failClaimPreFinalization('Failed to identify or create Auth user.')
        return new Response(JSON.stringify({ error: 'Server error: Failed to identify or create Auth user.' }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 5. Persist auth_user_id
      if (targetApp.auth_user_id !== userId) {
        const { error: updateAuthIdErr } = await supabaseAdmin.from('account_applications').update({ auth_user_id: userId }).eq('id', applicationId)
        if (updateAuthIdErr) {
          console.error(`[manage-account-applications] HTTP 500: Failed to persist auth_user_id: ${updateAuthIdErr.message}`)
          await failClaimPreFinalization('Failed to persist auth_user_id mapping.')
          return new Response(JSON.stringify({ error: 'Server error: Failed to persist auth user mapping.' }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      }

      // 6. Create or validate the profile. Validate role, organization_id, municipality_id, and account_status.
      const { data: profileCheck, error: profileFetchErr } = await supabaseAdmin
        .from('profiles')
        .select('id, role, account_status, organization_id, municipality_id')
        .eq('id', userId)
        .maybeSingle()

      if (profileFetchErr) {
        console.error(`[manage-account-applications] HTTP 500: Profile fetch failed: ${profileFetchErr.message}`)
        await failClaimPreFinalization('Failed to query user profile.')
        return new Response(JSON.stringify({ error: 'Server error: Failed to query user profile.' }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      if (profileCheck) {
        if (
          profileCheck.role !== derivedRole ||
          profileCheck.organization_id !== organizationId ||
          profileCheck.account_status !== 'active' ||
          (profileCheck.municipality_id || null) !== (targetApp.municipality_id || null)
        ) {
          const msg = 'Conflict: Existing profile attributes mismatch expected role, organization, municipality, or account_status.'
          await failClaimPreFinalization(msg)
          return new Response(JSON.stringify({ error: msg }), {
            status: 409,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      } else {
        const { error: profileCreateError } = await supabaseAdmin.from('profiles').insert({
          id: userId,
          full_name: targetApp.full_name,
          role: derivedRole,
          account_status: 'active',
          municipality_id: targetApp.municipality_id || null,
          organization_id: organizationId,
        })

        if (profileCreateError) {
          console.error(`[manage-account-applications] Profile insert failed: ${profileCreateError.message}`)
          await failClaimPreFinalization('Failed to create user profile.')
          return new Response(JSON.stringify({ error: 'Server error: Failed to create profile.' }), {
            status: 500,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      }

      // 7. Call finalize_approval_claim while application is PENDING and claim is CLAIMED
      const { data: finalizeResult, error: finalizeError } = await supabaseAdmin.rpc('finalize_approval_claim', {
        p_app_id: applicationId,
        p_owner_token: ownerToken,
        p_admin_id: user.id,
      })

      if (finalizeError || finalizeResult !== true) {
        const msg = finalizeError?.message || 'Claim finalization returned non-true value.'
        console.error(`[manage-account-applications] Finalize claim failed: ${msg}`)
        await failClaimPreFinalization(`Claim finalization failed: ${msg}`)
        return new Response(JSON.stringify({ error: 'Critical error: Claim finalization failed.' }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // Application is now APPROVED and claim is PROVISIONED.
      // 8. Transition invitation state from NOT_ATTEMPTED to ATTEMPT_IN_PROGRESS (Requires APPROVED & PROVISIONED in Staging)
      if (inviteState === 'NOT_ATTEMPTED') {
        const { data: transStartData, error: transStartErr } = await supabaseAdmin.rpc('transition_invitation_state', {
          p_app_id: applicationId,
          p_owner_token: ownerToken,
          p_from_state: 'NOT_ATTEMPTED',
          p_to_state: 'ATTEMPT_IN_PROGRESS',
          p_error_code: null,
        })

        if (transStartErr || !transStartData || transStartData.length === 0 || !transStartData[0].success) {
          const msg = transStartData?.[0]?.error_message || transStartErr?.message || 'Failed to start invitation transition.'
          console.error(`[manage-account-applications] transition_invitation_state (ATTEMPT_IN_PROGRESS) failed: ${msg}`)
        }
      }

      // 9. Generate and issue the activation OTP using issue_account_otp
      const rawCode = generateSecureSixDigitCode()
      const codeHash = await computeHmacSha256(otpSecret, rawCode)

      const { data: otpIdData, error: otpError } = await supabaseAdmin.rpc('issue_account_otp', {
        p_application_id: applicationId,
        p_user_id: null,
        p_email: targetApp.email,
        p_code_hash: codeHash,
        p_code_type: 'ACTIVATION',
        p_ttl_minutes: 60
      })

      if (otpError || !otpIdData) {
        console.error(`[manage-account-applications] OTP issuance failed after approval: ${otpError?.message || 'No OTP ID returned'}`)
        return new Response(JSON.stringify({
          error: 'Account provisioned and approved, but activation OTP issuance failed. System Owner can re-issue code by retrying.'
        }), {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // 10. After successful OTP issuance, transition ATTEMPT_IN_PROGRESS (or current) to INVITATION_CONFIRMED
      const fromStateForConf = inviteState === 'NOT_ATTEMPTED' ? 'ATTEMPT_IN_PROGRESS' : inviteState
      if (fromStateForConf !== 'INVITATION_CONFIRMED') {
        const { data: transConfData, error: transConfErr } = await supabaseAdmin.rpc('transition_invitation_state', {
          p_app_id: applicationId,
          p_owner_token: ownerToken,
          p_from_state: fromStateForConf,
          p_to_state: 'INVITATION_CONFIRMED',
          p_error_code: null,
        })

        if (transConfErr || !transConfData || transConfData.length === 0 || !transConfData[0].success) {
          const msg = transConfData?.[0]?.error_message || transConfErr?.message || 'Confirmation state update failed.'
          console.error(`[manage-account-applications] transition_invitation_state (INVITATION_CONFIRMED) failed: ${msg}`)
        }
      }

      // Audit log insert with result check (Never logging activation code)
      const { error: auditInsertErr } = await supabaseAdmin.from('audit_logs').insert({
        actor_id: user.id,
        action: 'APPROVE_ORGANIZATION_APPLICATION',
        table_name: 'account_applications',
        record_id: applicationId,
        details: { organization_id: organizationId, organization_type: orgType }
      })

      if (auditInsertErr) {
        console.error(`[manage-account-applications] Audit log insert error: ${auditInsertErr.message}`)
      }

      // 11. Return raw activation code only to authenticated System Owner
      return new Response(JSON.stringify({
        success: true,
        message: 'Organization application approved and activation code generated successfully.',
        activationCode: rawCode
      }), {
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

      if (rejectError || rejectResult !== true) {
        return new Response(JSON.stringify({ error: 'Failed to reject application or application status conflict.' }), {
          status: 409,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      const { error: auditRejectErr } = await supabaseAdmin.from('audit_logs').insert({
        actor_id: user.id,
        action: 'REJECT_ORGANIZATION_APPLICATION',
        table_name: 'account_applications',
        record_id: applicationId,
        details: {}
      })

      if (auditRejectErr) {
        console.error(`[manage-account-applications] Audit log insert error on reject: ${auditRejectErr.message}`)
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
    console.error(`[manage-account-applications] Unhandled exception: ${errorMessage}`)
    return new Response(JSON.stringify({ error: 'Internal server error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
