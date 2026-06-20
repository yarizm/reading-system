import pytest
import requests
import time
import base64
from conftest import create_custom_token, sign_payload

# ----------------- TIER 3: CROSS-FEATURE COMBINATIONS -----------------

def test_combination_token_generation_tampering(backend_url, token_secret):
    """Combination 1: Verify token is valid, then tamper signature and verify it gets rejected with 401"""
    token = create_custom_token(1, token_secret)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code != 401
    
    # Tamper the token by replacing some bytes at the end
    tampered_token = token[:-4] + "AAAA"
    headers_tampered = {"X-User-Token": tampered_token}
    resp_tampered = requests.get(f"{backend_url}/sysUser/me", headers=headers_tampered)
    assert resp_tampered.status_code == 401

def test_combination_single_vs_double_base64url(backend_url, token_secret):
    """Combination 2: Verify that a single base64url encoded token (wrong format) is rejected, while double is accepted"""
    # Single Base64Url: payload + "." + signature
    payload = f"1.{int(time.time() * 1000)}"
    signature = sign_payload(payload, token_secret)
    single_token = f"{payload}.{signature}"  # Not double base64Url encoded
    
    headers_single = {"X-User-Token": single_token}
    resp_single = requests.get(f"{backend_url}/sysUser/me", headers=headers_single)
    assert resp_single.status_code == 401
    
    # Double base64Url: double encoded payload
    double_token = create_custom_token(1, token_secret)
    headers_double = {"X-User-Token": double_token}
    resp_double = requests.get(f"{backend_url}/sysUser/me", headers=headers_double)
    assert resp_double.status_code != 401

def test_combination_whitelist_vs_non_whitelist_with_token_states(backend_url, token_secret, token_ttl):
    """Combination 3: GET /sysBook/list works with/without token, but POST /sysBook/add requires a valid token (rejects invalid)"""
    # GET /sysBook/list works without token
    resp_list_no_token = requests.get(f"{backend_url}/sysBook/list")
    assert resp_list_no_token.status_code != 401
    
    # GET /sysBook/list works with valid token
    valid_token = create_custom_token(1, token_secret)
    resp_list_valid_token = requests.get(f"{backend_url}/sysBook/list", headers={"X-User-Token": valid_token})
    assert resp_list_valid_token.status_code != 401
    
    # POST /sysBook/add strictly rejects missing token
    resp_add_no_token = requests.post(f"{backend_url}/sysBook/add", json={})
    assert resp_add_no_token.status_code == 401
    
    # POST /sysBook/add strictly rejects expired token
    expired_issued_at = int(time.time() * 1000) - token_ttl - 5000
    expired_token = create_custom_token(1, token_secret, issued_at=expired_issued_at)
    resp_add_expired_token = requests.post(f"{backend_url}/sysBook/add", json={}, headers={"X-User-Token": expired_token})
    assert resp_add_expired_token.status_code == 401

def test_combination_custom_token_expiration_timing(backend_url, token_secret, token_ttl):
    """Combination 4: Verify custom token expiration timing: create token 1s before expiration, verify accepted, sleep 1.5s, verify rejected"""
    # Create a token that expires in 1000ms (1 second)
    issued_at = int(time.time() * 1000) - token_ttl + 1000
    token = create_custom_token(1, token_secret, issued_at=issued_at)
    
    # Test 1: Immediate request is accepted
    resp1 = requests.get(f"{backend_url}/sysUser/me", headers={"X-User-Token": token})
    assert resp1.status_code != 401
    
    # Sleep 1.5 seconds to let the token cross the expiration boundary
    time.sleep(1.5)
    
    # Test 2: Request after sleep is rejected as expired
    resp2 = requests.get(f"{backend_url}/sysUser/me", headers={"X-User-Token": token})
    assert resp2.status_code == 401

def test_combination_token_verification_flow(backend_url, token_secret):
    """Combination 5: Validate token verification flow with custom token on /sysUser/me (non-whitelisted)"""
    token = create_custom_token(1, token_secret)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code != 401
    data = resp.json()
    # If the response is success, verify it contains code 200 or user details
    # Otherwise, it might be 404/500 if user 1 doesn't exist yet, but not 401
    if resp.status_code == 200:
        assert data.get("code") == "0" or "username" in data.get("data", {})
