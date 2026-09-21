#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Yay! アカウント生成スクリプト (調査・脆弱性検証用)
=======================================================
【重要】
- web API key は使用しない（使用不可のため）
- アプリ版の署名方式（MD5 signed_info + HmacSHA256 signed_version）を使用
- メール認証フローは v1/email_verification_urls → idcardcheck.com → v3/users/register
- Google 認証は不要（メールアドレスのみで登録可能なフロー）
- アカウント量産が実際に可能かどうかを検証する目的

依存: pip install httpx
"""

import hashlib
import hmac
import base64
import json
import random
import time
import uuid as uuid_lib
from dataclasses import dataclass
from typing import Optional

import httpx

# ═══════════════════════════════════════════════
#  定数
# ═══════════════════════════════════════════════

BASE_URL             = "https://api.yay.space"
ID_CARD_CHECK_HOST   = "https://idcardcheck.com"

# アプリ版 API キー（apk から抽出）
API_KEY              = "ccd59ee269c01511ba763467045c115779fcae3050238a252f1bd1a4b65cfec6"

# API バージョンキー（apk から抽出）
API_VERSION_KEY      = "34c8c1cdf29b46a492e8ec58e6db37ec"
API_VERSION_NAME     = "4.26"
APP_VERSION          = "4.26.1"

# signed_info の HMAC ペッパー
SIGNED_INFO_PEPPER   = "yayZ1"
# signed_version のプレフィックス
SIGNED_VERSION_PREFIX = "yay_android"

# テスト用固定パスワード
DEFAULT_PASSWORD     = "TestPassword2024!"

# ═══════════════════════════════════════════════
#  デバイス情報生成
# ═══════════════════════════════════════════════

DEVICE_TEMPLATES = [
    {"prefix": "SM-",    "series": ["S", "A", "G", "N"],
     "digits": 4,        "suffix": ["B", "N", "U", "W"]},
    {"prefix": "Pixel ", "series": [
        "6", "6a", "6 Pro", "7", "7a", "7 Pro",
        "8", "8a", "8 Pro", "9", "9 Pro"
    ]},
    {"prefix": "SO-",    "digits": 2, "suffix": ["A", "B", "E", "G"]},
    {"prefix": "SOG",    "digits": 2, "suffix": [""]},
    {"prefix": "CPH",    "digits": 4, "suffix": [""]},
    {"prefix": "moto ",  "series": [
        "g14", "g24", "g34", "g54", "g84",
        "e13", "e14", "edge 40"
    ]},
]

BASE_RESOLUTIONS = [
    (1080, 2340), (1080, 2400), (1080, 2412), (1080, 2460),
    (1080, 2520), (1176, 2400), (1200, 2640), (1440, 3120),
    (720,  1600), (1080, 1920),
]
BASE_DENSITIES   = [1.75, 2.0, 2.25, 2.5, 2.75, 3.0, 3.5]
ANDROID_VERSIONS = ["11", "12", "12.1", "13", "14"]
BUILD_PREFIXES   = [
    "TP1A", "TQ3A", "UP1A", "UQ1A", "AP1A",
    "RQ3A", "SP1A", "TQ1A"
]


def _random_build_id() -> str:
    prefix = random.choice(BUILD_PREFIXES)
    date   = (
        f"{random.randint(22, 25)}"
        f"{random.randint(1, 12):02d}"
        f"{random.randint(1, 28):02d}"
    )
    patch = f"{random.randint(1, 99):03d}"
    return f"{prefix}.{date}.{patch}"


def _random_model() -> str:
    tmpl        = random.choice(DEVICE_TEMPLATES)
    prefix      = tmpl["prefix"]
    suffix_list = tmpl.get("suffix", [""])
    digits      = tmpl.get("digits", 3)

    if "series" in tmpl and "digits" not in tmpl:
        return prefix + random.choice(tmpl["series"])

    if "series" in tmpl:
        mid = random.choice(tmpl["series"])
        num = "".join(str(random.randint(0, 9)) for _ in range(digits))
        return prefix + mid + num + random.choice(suffix_list)

    num = "".join(str(random.randint(0, 9)) for _ in range(digits))
    return prefix + num + random.choice(suffix_list)


def generate_device_info() -> dict:
    base_w, base_h = random.choice(BASE_RESOLUTIONS)
    width   = base_w + random.randint(-4, 4)
    height  = base_h + random.randint(-8, 8)
    density = random.choice(BASE_DENSITIES) + random.uniform(-0.05, 0.05)
    return {
        "uuid":       str(uuid_lib.uuid4()),
        "model":      _random_model(),
        "resolution": f"{width}x{height}",
        "density":    f"{density:.4f}",
        "os_version": random.choice(ANDROID_VERSIONS),
        "build_id":   _random_build_id(),
    }


# ═══════════════════════════════════════════════
#  署名生成
# ═══════════════════════════════════════════════

def make_signed_info(device_uuid: str, timestamp: int) -> str:
    """
    signed_info = MD5(api_key + device_uuid + timestamp + "yayZ1")
    """
    raw = f"{API_KEY}{device_uuid}{timestamp}{SIGNED_INFO_PEPPER}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def make_signed_version() -> str:
    """
    signed_version = Base64(HMAC-SHA256(version_key, "yay_android/{version}"))
    """
    msg    = f"{SIGNED_VERSION_PREFIX}/{API_VERSION_NAME}"
    digest = hmac.new(
        API_VERSION_KEY.encode("utf-8"),
        msg.encode("utf-8"),
        digestmod=hashlib.sha256,
    ).digest()
    return base64.b64encode(digest).decode("ascii")


# ═══════════════════════════════════════════════
#  ヘッダー生成
# ═══════════════════════════════════════════════

def build_headers(device: dict, include_content_type: bool = False) -> dict:
    signed_version = make_signed_version()
    headers = {
        "Host":                "api.yay.space",
        "Accept":              "application/json",
        "User-Agent":          (
            f"yay/{APP_VERSION} Android/{device['os_version']} "
            f"({device['model']}; Build/{device['build_id']})"
        ),
        "X-App-Version":       API_VERSION_NAME,
        "X-Device-Info":       (
            f"yay {APP_VERSION} Android, {device['model']}, "
            f"{device['os_version']}, Build/{device['build_id']}"
        ),
        "X-Device-UUID":       device["uuid"],
        "X-Screen-Resolution": device["resolution"],
        "X-Screen-Density":    device["density"],
        "Authorization":       f"Bearer {signed_version}",
    }
    if include_content_type:
        headers["Content-Type"] = "application/json;charset=UTF-8"
    return headers


# ═══════════════════════════════════════════════
#  ランダムプロフィール生成
# ═══════════════════════════════════════════════

ADJECTIVES = ["happy", "cool", "fast", "bright", "calm", "wild", "soft", "sharp"]
NOUNS      = ["star", "moon", "wave", "cloud", "bird", "fox", "bear", "wolf"]


def random_nickname() -> str:
    adj  = random.choice(ADJECTIVES)
    noun = random.choice(NOUNS)
    num  = random.randint(100, 9999)
    return f"{adj}_{noun}{num}"


def random_birth_date() -> str:
    """2000〜2005 年のランダム生年月日"""
    year  = random.randint(2000, 2005)
    month = random.randint(1, 12)
    days_in_month = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    day   = random.randint(1, days_in_month[month - 1])
    return f"{year}-{month:02d}-{day:02d}"


# ═══════════════════════════════════════════════
#  Step 1: presigned_url 取得
# ═══════════════════════════════════════════════

def get_verification_url(client: httpx.Client, device: dict, email: str) -> Optional[str]:
    """
    POST /v1/email_verification_urls
    → presigned_url を返す（メール認証フロー開始）
    """
    ts          = int(time.time())
    signed_info = make_signed_info(device["uuid"], ts)
    signed_ver  = make_signed_version()

    payload = {
        "device_uuid":    device["uuid"],
        "email":          email,
        "locale":         "ja",
        "intent":         "sign_up",
        "api_key":        API_KEY,
        "timestamp":      str(ts),
        "signed_info":    signed_info,
        "signed_version": signed_ver,
        "app_version":    API_VERSION_NAME,
        "uuid":           device["uuid"],
    }

    print(f"  [Step1] POST /v1/email_verification_urls")
    resp = client.post(
        "/v1/email_verification_urls",
        data=payload,
        headers=build_headers(device),
    )
    _dump(resp, "Step1")
    if resp.status_code not in (200, 201):
        return None

    body = resp.json()
    return body.get("presigned_url") or body.get("url")


# ═══════════════════════════════════════════════
#  Step 2: 認証コード送信
# ═══════════════════════════════════════════════

def send_verification_code(device: dict, presigned_url: str, email: str) -> bool:
    """
    POST {presigned_url}
    → メールアドレスに確認コードが送信される
    """
    with httpx.Client(timeout=30.0) as raw_client:
        print(f"  [Step2] POST {presigned_url[:60]}...")
        resp = raw_client.post(
            presigned_url,
            data={"locale": "ja", "email": email},
            headers=build_headers(device),
        )
        _dump(resp, "Step2")
        return resp.status_code in (200, 201, 204)


# ═══════════════════════════════════════════════
#  Step 3: email_grant_token 取得
# ═══════════════════════════════════════════════

def get_email_grant_token(device: dict, email: str, code: str) -> Optional[str]:
    """
    POST https://idcardcheck.com/apis/v1/apps/yay/email_grant_tokens
    → email_grant_token を返す
    """
    with httpx.Client(timeout=30.0) as raw_client:
        print(f"  [Step3] POST idcardcheck.com/email_grant_tokens")
        resp = raw_client.post(
            f"{ID_CARD_CHECK_HOST}/apis/v1/apps/yay/email_grant_tokens",
            json={"code": int(code), "email": email},
            headers={
                **build_headers(device, include_content_type=True),
                "Host": "idcardcheck.com",
            },
        )
        _dump(resp, "Step3")
        if resp.status_code not in (200, 201):
            return None

        body = resp.json()
        return (
            body.get("email_grant_token")
            or body.get("token")
            or body.get("grant_token")
        )


# ═══════════════════════════════════════════════
#  Step 4: アカウント登録
# ═══════════════════════════════════════════════

def register_account(
    device: dict,
    email: str,
    password: str,
    email_grant_token: str,
    nickname: str,
    birth_date: str,
    gender: int = 0,
    country_code: str = "JP",
    proxy_url: Optional[str] = None,
) -> dict:
    """
    POST /v3/users/register
    → アカウント作成。access_token / refresh_token / user_id を返す
    """
    ts          = int(time.time())
    signed_info = make_signed_info(device["uuid"], ts)
    signed_ver  = make_signed_version()

    body = {
        "api_key":           API_KEY,
        "timestamp":         ts,
        "signed_info":       signed_info,
        "signed_version":    signed_ver,
        "app_version":       API_VERSION_NAME,
        "uuid":              device["uuid"],
        "nickname":          nickname,
        "birth_date":        birth_date,
        "gender":            gender,
        "country_code":      country_code,
        "email":             email,
        "password":          password,
        "email_grant_token": email_grant_token,
    }

    kwargs = {
        "base_url": BASE_URL,
        "timeout":  30.0,
    }
    if proxy_url:
        kwargs["proxy"] = proxy_url

    with httpx.Client(**kwargs) as reg_client:
        print(f"  [Step4] POST /v3/users/register")
        resp = reg_client.post(
            "/v3/users/register",
            json=body,
            headers=build_headers(device, include_content_type=True),
        )
        _dump(resp, "Step4/Register")

        result_body = resp.json()
        if resp.status_code in (200, 201):
            return {
                "success":       True,
                "user_id":       result_body.get("id") or result_body.get("user_id"),
                "nickname":      nickname,
                "email":         email,
                "access_token":  result_body.get("access_token"),
                "refresh_token": result_body.get("refresh_token"),
                "uuid":          device["uuid"],
                "raw":           result_body,
            }
        else:
            ec = result_body.get("error_code", "")
            msg = result_body.get("message", "")
            return {
                "success": False,
                "status":  resp.status_code,
                "error_code": ec,
                "message": msg,
                "uuid":    device["uuid"],
            }


# ═══════════════════════════════════════════════
#  対話型メインフロー
# ═══════════════════════════════════════════════

def interactive_register():
    device = generate_device_info()

    print("=" * 60)
    print(f"  Yay! Register (app={APP_VERSION} / api={API_VERSION_NAME})")
    print("=" * 60)
    print(f"  UUID       : {device['uuid']}")
    print(f"  Model      : {device['model']}")
    print(f"  OS         : Android {device['os_version']}")
    print(f"  Build      : {device['build_id']}")
    print(f"  Resolution : {device['resolution']}")
    print()

    # ── メールアドレス入力 ──
    email = input("  Email: ").strip()
    if not email:
        print("  エラー: メールアドレスを入力してください")
        return None

    with httpx.Client(base_url=BASE_URL, timeout=30.0) as client:

        # Step 1: presigned_url 取得
        presigned_url = None
        while True:
            try:
                presigned_url = get_verification_url(client, device, email)
                if presigned_url:
                    break
                print("  presigned_url 取得失敗")
            except httpx.HTTPStatusError as e:
                print(f"  HTTP エラー: {e.response.status_code}")
            except Exception as e:
                print(f"  接続エラー: {e}")

            if input("  リトライ? (y/N): ").lower() != "y":
                return None

        # Step 2: 認証コード送信
        while True:
            try:
                ok = send_verification_code(device, presigned_url, email)
                if ok:
                    print("  ✅ 認証コードを送信しました")
                    break
                print("  送信失敗")
            except Exception as e:
                print(f"  エラー: {e}")

            if input("  リトライ? (y/N): ").lower() != "y":
                return None

        # Step 3: 認証コード入力 → email_grant_token 取得
        email_grant_token = None
        while True:
            code = input("  認証コード: ").strip()
            if code.lower() == "q":
                return None
            try:
                email_grant_token = get_email_grant_token(device, email, code)
                if email_grant_token:
                    print("  ✅ email_grant_token 取得成功")
                    break
                print("  トークン取得失敗")
            except Exception as e:
                print(f"  エラー: {e}")

            if input("  リトライ? (y/N): ").lower() != "y":
                return None

    # Step 4: プロフィール入力 → 登録
    print()
    nickname   = input(f"  ニックネーム [{random_nickname()}]: ").strip() or random_nickname()
    birth_date = random_birth_date()
    print(f"  生年月日 (自動): {birth_date}")
    password   = DEFAULT_PASSWORD
    print(f"  パスワード: {password}")

    print()
    print("  VPN/プロキシを変更してから登録してください")
    proxy_url = input("  プロキシURL (空=なし): ").strip() or None

    result = register_account(
        device=device,
        email=email,
        password=password,
        email_grant_token=email_grant_token,
        nickname=nickname,
        birth_date=birth_date,
        proxy_url=proxy_url,
    )

    print()
    print("=" * 60)
    if result["success"]:
        print("  ✅ 登録成功！")
        print(f"  User ID      : {result['user_id']}")
        print(f"  Nickname     : {result['nickname']}")
        print(f"  Access Token : {str(result['access_token'])[:30]}...")
    else:
        print("  ❌ 登録失敗")
        print(f"  Status       : {result.get('status')}")
        print(f"  Error Code   : {result.get('error_code')}")
        print(f"  Message      : {result.get('message')}")
    print("=" * 60)

    return result


# ═══════════════════════════════════════════════
#  デバッグユーティリティ
# ═══════════════════════════════════════════════

def _dump(resp: httpx.Response, label: str = ""):
    print(f"  [{label}] {resp.status_code} {resp.url}")
    try:
        body = resp.json()
        print(f"  body: {json.dumps(body, ensure_ascii=False)[:200]}")
    except Exception:
        print(f"  body(raw): {resp.text[:200]}")


def verify_signatures():
    """署名生成の確認テスト"""
    print("=== 署名確認 ===")
    test_uuid = "test-uuid-1234-5678"
    test_ts   = 1700000000
    print(f"  API_KEY        : {API_KEY[:20]}...")
    print(f"  API_VERSION_KEY: {API_VERSION_KEY[:20]}...")
    print(f"  signed_info    : {make_signed_info(test_uuid, test_ts)}")
    print(f"  signed_version : {make_signed_version()}")
    print()


# ═══════════════════════════════════════════════
#  エントリーポイント
# ═══════════════════════════════════════════════

if __name__ == "__main__":
    import sys

    if "--verify" in sys.argv:
        verify_signatures()
        sys.exit(0)

    print()
    print("Yay! アカウント生成ツール")
    print("※ 調査・脆弱性検証専用。不正利用禁止。")
    print()
    print("注意事項:")
    print("  - web APIキーは使用不可のため、アプリ版署名を使用")
    print("  - メール認証フロー（presigned_url → idcardcheck）が必要")
    print("  - Google認証は不要（メールのみで登録可能なことを検証）")
    print("  - IPレート制限があるためVPN/プロキシの利用を推奨")
    print()

    verify_signatures()
    interactive_register()
