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
      console.error('[activate-account] Missing required server environment variables.')
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

    const { email, code, newPassword } = body
    const cleanEmail = (email || '').toString().trim().toLowerCase()
    const cleanCode = (code || '').toString().trim()
    const cleanPassword = (newPassword || '').toString()

    if (!cleanEmail || !EMAIL_REGEX.test(cleanEmail) || !cleanCode || cleanCode.length !== 6 || !cleanPassword || cleanPassword.length < 6) {
      return new Response(JSON.stringify({ error: 'Validation failed: Valid email, 6-digit activation code, and password (min 6 chars) are required.' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey)

    // Compute HMAC-SHA256 hash of activation code
    const codeHash = await computeHmacSha256(otpSecret, cleanCode)

    // Verify activation OTP
    const { data: verifyData, error: verifyError } = await supabaseAdmin.rpc('verify_account_otp', {
      p_email: cleanEmail,
      p_code_hash: codeHash,
      p_code_type: 'ACTIVATION'
    })

    if (verifyError || !verifyData || verifyData.length === 0 || !verifyData[0].success) {
      const errMessage = verifyData?.[0]?.error_message || verifyError?.message || 'Invalid or expired activation code.'
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

    // Update password through Supabase Auth Admin API
    const { error: updateError } = await supabaseAdmin.auth.admin.updateUserById(userId, {
      password: cleanPassword
    })

    if (updateError) {
      console.error(`[activate-account] Password update failed for user ${userId}: ${updateError.message}`)
      return new Response(JSON.stringify({ error: 'Failed to set password in authentication provider.' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Complete activation OTP using existing one-argument RPC
    const { data: compData, error: compError } = await supabaseAdmin.rpc('complete_account_otp', {
      p_otp_id: otpId
    })

    if (compError || compData !== true) {
      console.error(`[activate-account] complete_account_otp failed for OTP ${otpId}: ${compError?.message || 'returned non-true value'}`)
      return new Response(JSON.stringify({ error: 'Password updated successfully, but account activation finalization failed.' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    return new Response(JSON.stringify({ success: true, message: 'Account activated and password set successfully.' }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (err) {
    console.error(`[activate-account] Unhandled exception: ${err instanceof Error ? err.message : String(err)}`)
    return new Response(JSON.stringify({ error: 'Internal server error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
