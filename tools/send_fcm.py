#!/usr/bin/env python3
"""
Sends a message with the app logo to every phone (topic "all"). It is a "data" message: the app builds the
notification itself (logo, brand colour, optional picture) and it is delivered also while the app is closed.

Setup (once):
  pip install google-auth requests
  Firebase console > Project settings > Service accounts > Generate new private key  (keep the file SECRET,
  never put it in the repository)

Usage:
  python tools/send_fcm.py service-account.json "Title" "Message text"
  python tools/send_fcm.py service-account.json "New version" "Version 2.6 is out" --link https://ivu.gt.tc --image https://example.com/banner.jpg
"""
import argparse
import json

import requests
import google.auth.transport.requests
from google.oauth2 import service_account


def main() -> None:
    parser = argparse.ArgumentParser(description="Send a Photo Video URL message through Firebase Cloud Messaging")
    parser.add_argument("key", help="service account JSON file")
    parser.add_argument("title")
    parser.add_argument("body")
    parser.add_argument("--link", default="", help="opened when the notification is tapped")
    parser.add_argument("--image", default="", help="https picture shown in the notification")
    parser.add_argument("--topic", default="all")
    args = parser.parse_args()

    credentials = service_account.Credentials.from_service_account_file(
        args.key, scopes=["https://www.googleapis.com/auth/firebase.messaging"]
    )
    credentials.refresh(google.auth.transport.requests.Request())
    with open(args.key, "r", encoding="utf-8") as handle:
        project_id = json.load(handle)["project_id"]

    data = {"title": args.title, "body": args.body}
    if args.link:
        data["link"] = args.link
    if args.image:
        data["image"] = args.image

    payload = {"message": {"topic": args.topic, "data": data, "android": {"priority": "HIGH"}}}
    response = requests.post(
        f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send",
        headers={"Authorization": "Bearer " + credentials.token, "Content-Type": "application/json"},
        data=json.dumps(payload),
        timeout=30,
    )
    print(response.status_code, response.text)


if __name__ == "__main__":
    main()
