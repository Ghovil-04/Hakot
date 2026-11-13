import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

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

async function sha256Hex(message: string): Promise<string> {
  const enc = new TextEncoder()
  const data = enc.encode(message)
  const hashBuffer = await crypto.subtle.digest("SHA-256", data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
}

function generateRecoveryToken(): string {
  const array = new Uint8Array(32)
  crypto.getRandomValues(array)
  return Array.from(array).map(b => b.toString(16).padStart(2, '0')).join('')
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
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    const otpSecret = Deno.env.get('OTP_HMAC_SECRET') ?? ''

    if (!supabaseUrl || !supabaseServiceKey || !otpSecret) {
      console.error('[reset-account-password] Missing server environment variables (SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, or OTP_HMAC_SECRET)')
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

    const { email, code, otpCode, newPassword } = body
    const cleanEmail = (email || '').toString().trim().toLowerCase()
    const cleanCode = (code || otpCode || '').toString().trim()
    const cleanPassword = (newPassword || '').toString()

    if (!cleanEmail || !EMAIL_REGEX.test(cleanEmail)) {
      return new Response(JSON.stringify({ error: 'Validation failed: A valid email address is required.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    if (!cleanCode) {
      return new Response(JSON.stringify({ error: 'Validation failed: Verification code (OTP) is required.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    if (!cleanPassword || cleanPassword.length < 6) {
      return new Response(JSON.stringify({ error: 'Validation failed: New password must be at least 6 characters long.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey)

    // Step 1: Compute HMAC hash of the OTP code
    const codeHash = await computeHmacSha256(otpSecret, cleanCode)

    // Step 2: Call verify_account_otp RPC
    const { data: verifyData, error: verifyError } = await supabaseAdmin.rpc('verify_account_otp', {
      p_email: cleanEmail,
      p_code_hash: codeHash,
      p_code_type: 'PASSWORD_RESET'
    })

    if (verifyError || !verifyData || verifyData.length === 0 || !verifyData[0].success) {
      const errMessage = verifyData?.[0]?.error_message || verifyError?.message || 'Invalid or expired verification code.'
      return new Response(JSON.stringify({ error: errMessage }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const otpId = verifyData[0].otp_id
    const userId = verifyData[0].user_id

    if (!otpId || !userId) {
      return new Response(JSON.stringify({ error: 'Invalid verification session state.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Step 3: Generate 256-bit recovery token and SHA-256 hash it
    const recoveryToken = generateRecoveryToken()
    const recoveryTokenHash = await sha256Hex(recoveryToken)

    // Step 4: Call register_recovery_token RPC
    const { data: regData, error: regError } = await supabaseAdmin.rpc('register_recovery_token', {
      p_otp_id: otpId,
      p_recovery_token_hash: recoveryTokenHash
    })

    if (regError || regData !== true) {
      return new Response(JSON.stringify({ error: 'Failed to initialize secure recovery session.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Step 5: Generate operation UUID and call authorize_password_update RPC
    const operationId = crypto.randomUUID()
    const { data: authData, error: authDataError } = await supabaseAdmin.rpc('authorize_password_update', {
      p_otp_id: otpId,
      p_recovery_token_hash: recoveryTokenHash,
      p_operation_id: operationId
    })

    if (authDataError || !authData || authData.length === 0 || !authData[0].success) {
      const errMessage = authData?.[0]?.error_message || authDataError?.message || 'Password update authorization failed.'
      return new Response(JSON.stringify({ error: errMessage }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Step 6: Update user password through Supabase Auth Admin API
    const { error: updateAuthError } = await supabaseAdmin.auth.admin.updateUserById(userId, {
      password: cleanPassword
    })

    if (updateAuthError) {
      console.error(`[reset-account-password] Supabase Auth update failed for user ${userId}: ${updateAuthError.message}`)

      // Handle failure: mark session UNCERTAIN using fail_password_update
      try {
        await supabaseAdmin.rpc('fail_password_update', {
          p_otp_id: otpId,
          p_recovery_token_hash: recoveryTokenHash,
          p_operation_id: operationId
        })
      } catch (failErr) {
        console.error(`[reset-account-password] Failed to mark update as uncertain: ${failErr instanceof Error ? failErr.message : 'Unknown'}`)
      }

      return new Response(JSON.stringify({ error: 'Failed to update user password. Session marked uncertain.' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Step 7: Only after Supabase Auth confirms success, call complete_account_otp RPC
    const { data: compData, error: compError } = await supabaseAdmin.rpc('complete_account_otp', {
      p_otp_id: otpId,
      p_recovery_token_hash: recoveryTokenHash,
      p_operation_id: operationId
    })

    if (compError || compData !== true) {
      console.error(`[reset-account-password] Auth succeeded but complete_account_otp failed for otpId ${otpId}: ${compError?.message || 'Returned false'}`)
      return new Response(JSON.stringify({
        success: true,
        warning: 'Password updated successfully in authentication provider, but session finalization requires administrative reconciliation.'
      }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    return new Response(JSON.stringify({
      success: true,
      message: 'Password updated and reset request completed successfully.'
    }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : 'Internal server error'
    console.error(`[reset-account-password] Unhandled exception: ${errorMessage}`)
    return new Response(JSON.stringify({ error: 'Internal server error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
