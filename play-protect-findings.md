# Play Protect audit findings

Source: https://developers.google.com/android/play-protect/warning-dev-guidance

Google documents that sideloading protections are especially triggered by applications downloaded from browsers, messaging apps, or file managers when they declare sensitive permissions such as RECEIVE_SMS, READ_SMS, notification-listener, or accessibility permissions. The documented developer actions are to use only the minimum permissions needed, use APIs for their intended purposes, follow privacy/security best practices, and request an appeal if a compliant app is incorrectly blocked.

Radar Proxy currently needs INTERNET and ACCESS_NETWORK_STATE for subscription fetches and active-network selection. It does not use SMS, notification-listener, accessibility, device-admin, VPN, contacts, storage, or overlay permissions. Play Protect verdicts cannot be bypassed by code; the legitimate remediation is to reduce unnecessary permissions, publish a signed release/AAB through Play Console, provide accurate app metadata and privacy disclosures, and appeal an incorrect verdict through Google's process.
