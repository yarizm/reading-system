import os
import base64
import hmac
import hashlib
import time
from pathlib import Path
import pytest
from dotenv import load_dotenv

# Load env variables from root .env
root_dir = Path(__file__).resolve().parent.parent
env_path = root_dir / '.env'
if env_path.exists():
    load_dotenv(env_path)
else:
    load_dotenv()

# Extract config or use defaults
APP_AUTH_TOKEN_SECRET = os.getenv("APP_AUTH_TOKEN_SECRET", "default_secret")
APP_AUTH_TOKEN_TTL_MILLIS = int(os.getenv("APP_AUTH_TOKEN_TTL_MILLIS", "86400000"))
ELASTIC_PASSWORD = os.getenv("ELASTIC_PASSWORD", "default_es_pass")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "default_mysql_pass")
BACKEND_URL = os.getenv("BACKEND_URL", "http://127.0.0.1:8090")
REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
ES_URL = os.getenv("ES_URL", "http://127.0.0.1:9200")

def base64url_encode(data: bytes) -> str:
    encoded = base64.urlsafe_b64encode(data).decode('utf-8')
    return encoded.rstrip('=')

def base64url_decode(s: str) -> bytes:
    rem = len(s) % 4
    if rem > 0:
        s += '=' * (4 - rem)
    return base64.urlsafe_b64decode(s.encode('utf-8'))

def sign_payload(payload: str, secret: str) -> str:
    signature = hmac.new(
        secret.encode('utf-8'),
        payload.encode('utf-8'),
        hashlib.sha256
    ).digest()
    return base64url_encode(signature)

def create_custom_token(user_id: int, secret: str, issued_at: int = None) -> str:
    if issued_at is None:
        issued_at = int(time.time() * 1000)
    payload = f"{user_id}.{issued_at}"
    signature = sign_payload(payload, secret)
    inner = f"{payload}.{signature}"
    return base64url_encode(inner.encode('utf-8'))

@pytest.fixture(scope="session")
def token_secret():
    return APP_AUTH_TOKEN_SECRET

@pytest.fixture(scope="session")
def token_ttl():
    return APP_AUTH_TOKEN_TTL_MILLIS

@pytest.fixture(scope="session")
def elastic_password():
    return ELASTIC_PASSWORD

@pytest.fixture(scope="session")
def mysql_password():
    return MYSQL_PASSWORD

@pytest.fixture(scope="session")
def backend_url():
    return BACKEND_URL

@pytest.fixture(scope="session")
def redis_host():
    return REDIS_HOST

@pytest.fixture(scope="session")
def redis_port():
    return REDIS_PORT

@pytest.fixture(scope="session")
def mysql_host():
    return MYSQL_HOST

@pytest.fixture(scope="session")
def mysql_port():
    return MYSQL_PORT

@pytest.fixture(scope="session")
def es_url():
    return ES_URL

@pytest.fixture(scope="session")
def token_helpers():
    return {
        "create_token": create_custom_token,
        "sign": sign_payload,
        "base64url_encode": base64url_encode,
        "base64url_decode": base64url_decode
    }
