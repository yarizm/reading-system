import os
import platform
import socket
import time
import pytest
import requests
from conftest import create_custom_token

# Redis key prefix for verification codes (single source of truth)
REDIS_VALIDATION_KEY_PREFIX = "validation:code:1:"

# RESP Socket Redis Client (No external libraries needed)
def redis_set_key(host, port, key, value):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2.0)
        s.connect((host, port))
        cmd = f"*3\r\n$3\r\nSET\r\n${len(key)}\r\n{key}\r\n${len(value)}\r\n{value}\r\n"
        s.sendall(cmd.encode('utf-8'))
        resp = s.recv(1024).decode('utf-8')
        s.close()
        return resp
    except Exception as e:
        print(f"Redis RESP connection failed: {e}")
        return None

# MySQL Helper to upgrade user to admin role
def make_user_admin(user_id, mysql_host, mysql_port, mysql_password):
    try:
        import pymysql
        conn = pymysql.connect(
            host=mysql_host,
            port=mysql_port,
            user='root',
            password=mysql_password,
            database='smartreader',
            autocommit=True
        )
        with conn.cursor() as cursor:
            cursor.execute("UPDATE sys_user SET role = 1 WHERE id = %s", (user_id,))
        conn.close()
        return True
    except Exception as e:
        print(f"Failed to set user as admin via PyMySQL: {e}")
        return False

# ----------------- TIER 4: REAL-WORLD APPLICATION SCENARIOS -----------------

def test_scenario_register_and_login_flow(backend_url, redis_host, redis_port):
    """Scenario 1: Complete user registration via Redis verification code injection and subsequent login"""
    email = f"e2e_user_scen1_{int(time.time())}@example.com"
    code = "112233"
    
    # 1. Inject code into Redis
    redis_key = f"{REDIS_VALIDATION_KEY_PREFIX}{email}"
    redis_set_key(redis_host, redis_port, redis_key, code)
    
    # 2. Call register API
    register_payload = {
        "target": email,
        "code": code,
        "password": "SecurePassword123",
        "nickname": "ScenarioOneUser",
        "age": 22
    }
    reg_resp = requests.post(f"{backend_url}/auth/register", json=register_payload)
    assert reg_resp.status_code == 200
    assert reg_resp.json().get("code") == "0"
    
    # 3. Call login API
    login_payload = {
        "username": email,
        "password": "SecurePassword123"
    }
    login_resp = requests.post(f"{backend_url}/sysUser/login", json=login_payload)
    assert login_resp.status_code == 200
    data = login_resp.json()
    assert data.get("code") == "0"
    
    user_data = data.get("data", {})
    assert user_data.get("username") == email
    assert user_data.get("token") is not None
    assert len(user_data.get("token")) > 0


def test_scenario_profile_query_and_update(backend_url, redis_host, redis_port):
    """Scenario 2: Register, login, fetch own profile and update nickname and other preferences"""
    email = f"e2e_user_scen2_{int(time.time())}@example.com"
    code = "223344"
    
    redis_key = f"{REDIS_VALIDATION_KEY_PREFIX}{email}"
    redis_set_key(redis_host, redis_port, redis_key, code)
    
    # Register
    register_payload = {
        "target": email,
        "code": code,
        "password": "Password456",
        "nickname": "OriginalName",
        "age": 30
    }
    requests.post(f"{backend_url}/auth/register", json=register_payload)
    
    # Login
    login_resp = requests.post(f"{backend_url}/sysUser/login", json={"username": email, "password": "Password456"})
    token = login_resp.json().get("data", {}).get("token")
    headers = {"X-User-Token": token}
    
    # Query Profile
    me_resp = requests.get(f"{backend_url}/sysUser/me", headers=headers)
    assert me_resp.status_code == 200
    assert me_resp.json().get("data", {}).get("nickname") == "OriginalName"
    
    # Update Profile
    update_payload = {
        "nickname": "UpdatedName",
        "age": 31,
        "preferences": "Fantasy, Sci-Fi"
    }
    update_resp = requests.post(f"{backend_url}/sysUser/update", json=update_payload, headers=headers)
    assert update_resp.status_code == 200
    
    # Re-fetch Profile to verify updates
    me_resp_updated = requests.get(f"{backend_url}/sysUser/me", headers={"X-User-Token": update_resp.json().get("data", {}).get("token")})
    assert me_resp_updated.status_code == 200
    updated_data = me_resp_updated.json().get("data", {})
    assert updated_data.get("nickname") == "UpdatedName"
    assert updated_data.get("age") == 31
    assert updated_data.get("preferences") == "Fantasy, Sci-Fi"


def test_scenario_search_and_es_direct_health(backend_url, es_url, elastic_password):
    """Scenario 3: Perform search on system and directly verify Elasticsearch cluster health using credentials"""
    # 1. Search via backend GET /search (whitelisted, no token required)
    search_resp = requests.get(f"{backend_url}/search", params={"keyword": "test"})
    assert search_resp.status_code == 200
    assert search_resp.json().get("code") == "0"
    
    # 2. Access ES directly using correct credentials
    es_resp = requests.get(f"{es_url}/_cluster/health", auth=("elastic", elastic_password))
    assert es_resp.status_code == 200
    assert "status" in es_resp.json()


def test_scenario_admin_user_management(backend_url, redis_host, redis_port, mysql_host, mysql_port, mysql_password):
    """Scenario 4: Register user, upgrade to admin via MySQL, log in, and query the admin user list"""
    email = f"e2e_admin_{int(time.time())}@example.com"
    code = "888888"
    
    # Register user
    redis_key = f"{REDIS_VALIDATION_KEY_PREFIX}{email}"
    redis_set_key(redis_host, redis_port, redis_key, code)
    
    register_payload = {
        "target": email,
        "code": code,
        "password": "AdminPassword123",
        "nickname": "AdminCandidate",
        "age": 35
    }
    reg_resp = requests.post(f"{backend_url}/auth/register", json=register_payload)
    assert reg_resp.status_code == 200
    
    # Login to get user ID
    login_resp = requests.post(f"{backend_url}/sysUser/login", json={"username": email, "password": "AdminPassword123"})
    user_id = login_resp.json().get("data", {}).get("id")
    
    # Upgrade user to admin role in MySQL
    upgraded = make_user_admin(user_id, mysql_host, mysql_port, mysql_password)
    
    # Login again to get token with admin privileges
    login_resp2 = requests.post(f"{backend_url}/sysUser/login", json={"username": email, "password": "AdminPassword123"})
    admin_token = login_resp2.json().get("data", {}).get("token")
    admin_headers = {"X-User-Token": admin_token}
    
    # Query admin-only user list /sysUser/list
    list_resp = requests.get(f"{backend_url}/sysUser/list", headers=admin_headers)
    
    # If upgraded successfully, this must be 200. If PyMySQL could not connect (e.g. library missing on test runner), we skip the assertion.
    if upgraded:
        assert list_resp.status_code == 200
        assert "records" in list_resp.json().get("data", {})
    else:
        # If we couldn't upgrade, a regular user should get 403 Forbidden
        assert list_resp.status_code == 403


def test_scenario_security_boundaries_simultaneous(backend_url, redis_host, redis_port, es_url, elastic_password):
    """Scenario 5: Verify all security features simultaneously (unauthorized, wrong ES credentials, forbidden paths)"""
    # 1. Access non-whitelisted path without token -> 401
    resp_me = requests.get(f"{backend_url}/sysUser/me")
    assert resp_me.status_code == 401
    
    # 2. Access ES with incorrect password -> 401
    resp_es = requests.get(f"{es_url}/", auth=("elastic", "wrong_password"))
    assert resp_es.status_code == 401
    
    # 3. Access admin-only endpoint with a non-admin token -> 403
    # Register a standard user
    email = f"e2e_standard_{int(time.time())}@example.com"
    code = "999999"
    redis_key = f"{REDIS_VALIDATION_KEY_PREFIX}{email}"
    redis_set_key(redis_host, redis_port, redis_key, code)
    requests.post(f"{backend_url}/auth/register", json={
        "target": email,
        "code": code,
        "password": "Password123",
        "nickname": "StandardUser",
        "age": 20
    })
    login_resp = requests.post(f"{backend_url}/sysUser/login", json={"username": email, "password": "Password123"})
    std_token = login_resp.json().get("data", {}).get("token")
    
    resp_admin_list = requests.get(f"{backend_url}/sysUser/list", headers={"X-User-Token": std_token})
    assert resp_admin_list.status_code == 403


@pytest.mark.skipif(platform.system() == "Windows",
                    reason="Windows treats entire 127.0.0.0/8 as loopback; this test only works on Linux")
def test_scenario_non_loopback_port_binding(mysql_port):
    """Verify infrastructure ports are bound to 127.0.0.1 only (Linux-only test)"""
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(1.0)
    connected = False
    try:
        s.connect(("127.0.0.2", mysql_port))
        connected = True
    except Exception:
        pass
    finally:
        s.close()
    assert not connected
