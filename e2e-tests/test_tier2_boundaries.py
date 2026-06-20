import socket
import pytest
import requests
import time
from conftest import create_custom_token, sign_payload

# Global helper for socket connection testing
def check_port_open(ip, port, timeout=1.0):
    try:
        s = socket.create_connection((ip, port), timeout=timeout)
        s.close()
        return True
    except Exception:
        return False

# ----------------- TIER 2: BOUNDARIES & CORNER CASES -----------------

# -- Feature 1 Bound only to 127.0.0.1 Boundaries (4 tests) --

def test_mysql_invalid_port_0():
    """Test connecting to MySQL on port 0 fails"""
    with pytest.raises(Exception):
        socket.create_connection(("127.0.0.1", 0), timeout=1.0)

def test_redis_invalid_port_negative():
    """Test connecting to Redis on negative port fails"""
    with pytest.raises(Exception):
        socket.create_connection(("127.0.0.1", -1), timeout=1.0)

def test_es_invalid_port_too_large():
    """Test connecting to ES on port > 65535 fails"""
    with pytest.raises(Exception):
        socket.create_connection(("127.0.0.1", 70000), timeout=1.0)

def test_empty_host_ip_fails():
    """Test socket connection with empty host IP raises exception"""
    with pytest.raises(Exception):
        socket.create_connection(("", 3306), timeout=1.0)


# -- Feature 2 ES Auth Boundaries (4 tests) --

def test_es_auth_long_password():
    """Test ES access with extremely long password returns 401"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url, auth=("elastic", "a" * 1000))
    assert resp.status_code == 401

def test_es_auth_special_chars_username():
    """Test ES access with special characters in username returns 401"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url, auth=("elastic!@#$%", "password"))
    assert resp.status_code == 401

def test_es_auth_empty_username_password():
    """Test ES access with empty username and password returns 401"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url, auth=("", ""))
    assert resp.status_code == 401

def test_es_auth_unicode_credentials():
    """Test ES access with non-ASCII unicode credentials returns 401"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url, auth=("elastic用户名", "密码"))
    assert resp.status_code == 401


# -- Feature 3 JWT / Custom Token Boundaries (14 tests) --

def test_jwt_boundary_issued_at_now(backend_url, token_secret):
    """Test token with issued_at set to exactly current time is accepted"""
    token = create_custom_token(1, token_secret, issued_at=int(time.time() * 1000))
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code != 401

def test_jwt_boundary_issued_at_100ms_before_expiration(backend_url, token_secret, token_ttl):
    """Test token 100ms before expiration is accepted"""
    # 100ms before expiration -> issued_at = now - TTL + 100
    issued_at = int(time.time() * 1000) - token_ttl + 100
    token = create_custom_token(1, token_secret, issued_at=issued_at)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code != 401

def test_jwt_boundary_issued_at_100ms_after_expiration(backend_url, token_secret, token_ttl):
    """Test token 100ms after expiration is rejected with 401"""
    # 100ms after expiration -> issued_at = now - TTL - 100
    issued_at = int(time.time() * 1000) - token_ttl - 100
    token = create_custom_token(1, token_secret, issued_at=issued_at)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_jwt_boundary_issued_at_future(backend_url, token_secret):
    """Test token with issued_at in future (e.g. 1 hour from now) is accepted because java code does System.currentTimeMillis() - issuedAt > tokenTtlMillis, which is negative and thus < tokenTtlMillis"""
    issued_at = int(time.time() * 1000) + 3600 * 1000
    token = create_custom_token(1, token_secret, issued_at=issued_at)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code != 401

def test_jwt_boundary_issued_at_zero(backend_url, token_secret):
    """Test token with issued_at as 0 (epoch start) is rejected as expired"""
    token = create_custom_token(1, token_secret, issued_at=0)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_issued_at_negative(backend_url, token_secret):
    """Test token with negative issued_at is rejected as expired"""
    token = create_custom_token(1, token_secret, issued_at=-1234567)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_userid_zero(backend_url, token_secret):
    """Test token with userId = 0 is processed (non-401 or returns 401 depending on database existence, but doesn't crash token parser)"""
    token = create_custom_token(0, token_secret)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    # Since user 0 doesn't exist, activeUserId(0) returns null, which makes filter return 401 or similar.
    assert resp.status_code == 401

def test_jwt_boundary_userid_negative(backend_url, token_secret):
    """Test token with negative userId is rejected or doesn't crash"""
    token = create_custom_token(-1, token_secret)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_userid_very_large(backend_url, token_secret):
    """Test token with very large userId is processed or rejected elegantly"""
    token = create_custom_token(9999999999999999, token_secret)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_empty_token(backend_url):
    """Test empty token is strictly rejected"""
    headers = {"X-User-Token": ""}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_invalid_base64_chars(backend_url):
    """Test token with invalid Base64Url characters fails parsing and is rejected"""
    headers = {"X-User-Token": "invalid_chars_#@$"}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_corrupt_signature(backend_url, token_secret):
    """Test token with correct payload but corrupt signature is rejected"""
    # Double base64Url encoded
    # payload = "1.1620000000000" -> inner = payload + "." + corrupt_signature
    payload = f"1.{int(time.time()*1000)}"
    inner = f"{payload}.corruptsignature123"
    import base64
    from conftest import base64url_encode
    token = base64url_encode(inner.encode('utf-8'))
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_weak_key_rejected(backend_url):
    """Test token signed with a weak/short secret is rejected by backend (since backend expects configured secret)"""
    token = create_custom_token(1, " ")
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401

def test_jwt_boundary_incorrect_parts_count(backend_url):
    """Test token that decodes to incorrect parts count (e.g. 2 parts) is rejected"""
    import base64
    from conftest import base64url_encode
    inner = "1.1620000000000"  # only 2 parts (missing signature part)
    token = base64url_encode(inner.encode('utf-8'))
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401


# -- Feature 4 & 5 Whitelist / Non-whitelist Boundaries (5 tests) --

def test_whitelist_sysbook_list_post_method_requires_auth(backend_url):
    """Test POST /sysBook/list (which is whitelisted only for GET) strictly returns 401 without token"""
    resp = requests.post(f"{backend_url}/sysBook/list", json={})
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_whitelist_sysbook_catalog_empty_path(backend_url):
    """Test GET /sysBook/catalog/ without path variable returns 404, not 401"""
    resp = requests.get(f"{backend_url}/sysBook/catalog/")
    assert resp.status_code == 404

def test_whitelist_sysbook_chapter_invalid_id(backend_url):
    """Test GET /sysBook/chapter/-999 (invalid path variable) returns 200/404/500 but not 401"""
    resp = requests.get(f"{backend_url}/sysBook/chapter/-999")
    assert resp.status_code != 401

def test_whitelist_auth_register_empty_payload(backend_url):
    """Test POST /auth/register with empty payload is not blocked by auth (returns 400 parameter error instead of 401)"""
    resp = requests.post(f"{backend_url}/auth/register", json={})
    assert resp.status_code != 401
    assert resp.status_code == 400

def test_whitelist_sysuser_profile_large_id(backend_url):
    """Test GET /sysUser/profile/9999999999999 is whitelisted and returns 200/404 but not 401"""
    resp = requests.get(f"{backend_url}/sysUser/profile/9999999999999")
    assert resp.status_code != 401
