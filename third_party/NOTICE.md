# Included dependencies

The companion includes LibADB Android BC 3.2.0, pinned to commit
`05edcc2858f540309b8fc065006aa531850daaf3` from
[osservatorionessuno/libadb-android-bc](https://github.com/osservatorionessuno/libadb-android-bc).
LibADB Android was developed by Muntashir Al-Islam and contributors. The BC fork
replaces native pairing with Java and Bouncy Castle. We use its Apache-2.0
license option. See [COPYING](libadb-COPYING.txt),
[Apache 2.0](libadb-Apache-2.0-LICENSE.txt),
[BSD 3-Clause](libadb-BSD-3-Clause-LICENSE.txt) and [MIT](libadb-MIT-LICENSE.txt).
Individual source copyright and license notices are retained.

Copyright notices in the included LibADB sources:

- Copyright 2013 Cameron Gutman — AdbConnection, AdbProtocol and AdbStream (BSD-3-Clause)
- Copyright 2020 Sam Palmer — AdbAuthenticationFailedException (BSD-3-Clause)
- Copyright 2013 Google Inc. — PRNGFixes (MIT)
- Copyright 2020 南宫雪珊 and Copyright 2022 Muntashir Al-Islam — android/AdbMdns

The corresponding BSD and MIT license texts above apply together with the
chosen Apache-2.0 option as specified by the individual source SPDX notices.

Our build modifies AdbConnection.java and PairingConnectionCtx.java: bounded
socket connect/read times, socket closure during pairing cancellation, volatile
pairing state, and removal of verbose peer-identity logging. Exact patches live
in `scripts/fetch-android-deps.py` and fail if upstream text changes. These changes
were made for Northland Thor on October 15, 2026.

Bouncy Castle Java 1.84 includes bcprov, bcutil, bctls and bcpkix under the
[Bouncy Castle license](BouncyCastle-LICENSE.html), copyright The Legion of the
Bouncy Castle Inc. Source: https://github.com/bcgit/bc-java/tree/r1rv84

Versions, source archive and SHA-256 checksums are pinned in
`android/dependencies.json`. Shizuku API and its separate manager are no longer
used by the companion.

AndroidX annotation 1.3.0 is included under the Apache License 2.0. Its license
is in [AndroidX-annotation-LICENSE.txt](AndroidX-annotation-LICENSE.txt).
Source: https://android.googlesource.com/platform/frameworks/support/

The private APK experiment downloads and runs official Apktool 2.12.1, an
Apache License 2.0 tool, with its download hash pinned in the script. Apktool is
not bundled in the companion or setup ZIP. Source and license:
https://github.com/iBotPeaches/Apktool/tree/v2.12.1
