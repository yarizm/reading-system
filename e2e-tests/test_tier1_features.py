import os
import socket
import pytest
import requests
import time
from conftest import create_custom_token, sign_payload

# Global helper for socket connection testing
def check_port_open(ip, port, timeout=2.0):
    try:
        s = socket.create_connection((ip, port), timeout=timeout)
        s.close()
        return True
    except Exception:
        return False

# Global helper for LAN IP
def get_host_lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.2"

# ----------------- TIER 1: FEATURE 1 (Ports binding 127.0.0.1) -----------------

def test_mysql_port_open_on_loopback():
    """Test MySQL port is accessible on 127.0.0.1"""
    assert check_port_open("127.0.0.1", 3306)

def test_mysql_port_closed_on_other_ip():
    """Test MySQL port is NOT accessible on another local/LAN IP"""
    lan_ip = get_host_lan_ip()
    if lan_ip != "127.0.0.1":
        assert not check_port_open(lan_ip, 3306)
    assert not check_port_open("127.0.0.2", 3306)

def test_redis_port_open_on_loopback():
    """Test Redis port is accessible on 127.0.0.1"""
    assert check_port_open("127.0.0.1", 6379)

def test_redis_port_closed_on_other_ip():
    """Test Redis port is NOT accessible on another local/LAN IP"""
    lan_ip = get_host_lan_ip()
    if lan_ip != "127.0.0.1":
        assert not check_port_open(lan_ip, 6379)
    assert not check_port_open("127.0.0.2", 6379)

def test_es_port_open_on_loopback():
    """Test Elasticsearch port is accessible on 127.0.0.1"""
    assert check_port_open("127.0.0.1", 9200)

def test_es_port_closed_on_other_ip():
    """Test Elasticsearch port is NOT accessible on another local/LAN IP"""
    lan_ip = get_host_lan_ip()
    if lan_ip != "127.0.0.1":
        assert not check_port_open(lan_ip, 9200)
    assert not check_port_open("127.0.0.2", 9200)


# ----------------- TIER 1: FEATURE 2 (Elasticsearch Auth) -----------------

def test_es_auth_correct_credentials(elastic_password):
    """Test ES access with correct credentials returns 200"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url, auth=("elastic", elastic_password))
    assert resp.status_code == 200

def test_es_auth_incorrect_password():
    """Test ES access with wrong password returns 401"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url, auth=("elastic", "wrong_pass_123"))
    assert resp.status_code == 401

def test_es_auth_incorrect_username(elastic_password):
    """Test ES access with wrong username returns 401"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url, auth=("not_elastic", elastic_password))
    assert resp.status_code == 401

def test_es_auth_no_credentials():
    """Test ES access with no credentials returns 401"""
    url = "http://127.0.0.1:9200/"
    resp = requests.get(url)
    assert resp.status_code == 401


# ----------------- TIER 1: FEATURE 3 (JWT Secret & TTL) -----------------

def test_jwt_secret_non_empty(token_secret):
    """Test token secret is configured and not empty"""
    assert token_secret is not None
    assert len(token_secret) > 0

def test_jwt_ttl_positive(token_ttl):
    """Test token TTL is positive"""
    assert token_ttl > 0

def test_jwt_valid_token_accepted(backend_url, token_secret):
    """Test that a valid signed token is accepted (non-401) by /sysUser/me"""
    # Create valid token for user 1 (super admin)
    token = create_custom_token(1, token_secret)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    # Since ID=1 user might or might not exist yet, we check it is not 401 Unauthorized
    assert resp.status_code != 401

def test_jwt_wrong_secret_token_rejected(backend_url):
    """Test that token signed with incorrect secret is strictly rejected with 401"""
    token = create_custom_token(1, "completely_wrong_secret_123")
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_jwt_expired_token_rejected(backend_url, token_secret, token_ttl):
    """Test that an expired token is strictly rejected with 401"""
    # Create token with issued_at in the past past the TTL limit
    past_issued_at = int(time.time() * 1000) - token_ttl - 10000
    token = create_custom_token(1, token_secret, issued_at=past_issued_at)
    headers = {"X-User-Token": token}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_jwt_token_with_authorization_header(backend_url, token_secret):
    """Test that Bearer token in Authorization header is also accepted (non-401)"""
    token = create_custom_token(1, token_secret)
    headers = {"Authorization": f"Bearer {token}"}
    resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert resp.status_code != 401


# ----------------- TIER 1: FEATURE 4 (Whitelisted Paths) -----------------

def test_whitelist_sysbook_list(backend_url):
    """Test GET /sysBook/list is whitelisted and does not return 401"""
    resp = requests.get(f"{backend_url}/sysBook/list")
    assert resp.status_code != 401

def test_whitelist_sysbook_hot(backend_url):
    """Test GET /sysBook/hot is whitelisted and does not return 401"""
    resp = requests.get(f"{backend_url}/sysBook/hot")
    assert resp.status_code != 401

def test_whitelist_sysbook_rank(backend_url):
    """Test GET /sysBook/rank is whitelisted and does not return 401"""
    resp = requests.get(f"{backend_url}/sysBook/rank")
    assert resp.status_code != 401

def test_whitelist_sysbook_recommend(backend_url):
    """Test GET /sysBook/recommend is whitelisted and does not return 401"""
    resp = requests.get(f"{backend_url}/sysBook/recommend")
    assert resp.status_code != 401

def test_whitelist_auth_sendcode(backend_url):
    """Test POST /auth/sendCode is whitelisted and does not return 401 (returns 400 due to bad request parameter instead)"""
    resp = requests.post(f"{backend_url}/auth/sendCode", json={})
    assert resp.status_code != 401
    assert resp.status_code == 200 or resp.status_code == 400

def test_whitelist_auth_register(backend_url):
    """Test POST /auth/register is whitelisted and does not return 401
    （未注册用户必须能访问注册接口，否则陷入"先有鸡还是先有蛋"的死锁）"""
    resp = requests.post(f"{backend_url}/auth/register", json={})
    assert resp.status_code != 401
    # 具体业务校验失败应该是 400，而不是 401
    assert resp.status_code in (200, 400)


# ----------------- TIER 1: FEATURE 5 (Non-Whitelisted Paths) -----------------

def test_non_whitelist_sysuser_me_returns_401(backend_url):
    """Test GET /sysUser/me strictly returns 401 when no token is present"""
    resp = requests.get(f"{backend_url}/sysUser/me")
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_non_whitelist_sysuser_update_returns_401(backend_url):
    """Test POST /sysUser/update strictly returns 401 when no token is present"""
    resp = requests.post(f"{backend_url}/sysUser/update", json={})
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_non_whitelist_sysbook_add_returns_401(backend_url):
    """Test POST /sysBook/add strictly returns 401 when no token is present"""
    resp = requests.post(f"{backend_url}/sysBook/add", json={})
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_non_whitelist_sysuser_delete_returns_401(backend_url):
    """Test DELETE /sysUser/1 strictly returns 401 when no token is present"""
    resp = requests.delete(f"{backend_url}/sysUser/1")
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"

def test_non_whitelist_sysuser_password_returns_401(backend_url):
    """Test POST /sysUser/password strictly returns 401 when no token is present"""
    resp = requests.post(f"{backend_url}/sysUser/password", json={})
    assert resp.status_code == 401
    assert resp.json().get("code") == "401"
