# Privacy Policy — Avenarius

_Last updated: 3 June 2026_

Avenarius ("the app") is an unofficial, open‑source third‑party client for the
**Max** messaging service (operated at `max.ru` / `oneme.ru`). This policy explains
what data the app handles and how.

## Who operates the app

Avenarius is an independent open‑source project (source code:
<https://github.com/eizemazal/avenarius>). It is **not** affiliated with, endorsed by,
or operated by the Max service or its owners.

**Avenarius has no servers of its own.** The app runs entirely on your device and
communicates directly with the Max service's servers. The Avenarius developer does
**not** receive, collect, store, or have any access to your messages, contacts, phone
number, or any other personal data.

## What data the app processes

To function as a messaging client, the app processes the following **on your device**
and/or sends it **directly to the Max servers** (never to the developer):

- **Phone number** — entered by you to sign in; sent to the Max servers to
  authenticate. The app stores only the resulting login token locally (see below).
- **Messages, photos, videos, files, and reactions** — exchanged with the Max
  servers and shown on your device. Media you send is uploaded to the Max servers;
  media you download is saved to your device's Downloads folder at your request.
- **Contacts and chats** — retrieved from the Max servers to display your chat list
  and conversations.

All of the above is handled by, and stored on, the **Max service** under
**Max's own privacy policy**. Please review the Max service's privacy terms for how
that data is stored and used on their side.

## Data stored locally on your device

The app keeps the following in its private, app‑only storage:

- a **login token** and a randomly generated **device identifier** (needed to keep
  you signed in and to talk to the Max servers);
- your **theme preference** (System / Dark / Light).

This data never leaves your device except as part of normal communication with the
Max servers. Logging out removes the login token; uninstalling the app removes all
locally stored data.

## Permissions the app requests

- **Internet** — to communicate with the Max servers.
- **Notifications** — to show new‑message notifications.
- **Foreground service** — to keep the connection alive for message delivery while
  the app is in the background.
- **Camera** — only when you choose to capture a photo or video to send; capture is
  performed by your system camera app.
- **Photos / media / files access** — only the specific items you pick to send; the
  app does not scan or upload your gallery.
- **Storage (Android 9 and below only)** — to save files you download to the
  Downloads folder.

## What the app does NOT do

- No analytics, tracking, advertising, or profiling.
- No data is sent to the developer or to any third party other than the Max service
  you are connecting to.
- No data is sold.

## Children

The app is not directed to children and does not knowingly process data from
children.

## Data deletion

- **Log out** in the app to remove the locally stored login token.
- **Uninstall** the app to remove all locally stored data from your device.
- Data held by the Max service is controlled by that service; use its tools to
  manage or delete your account and data there.

## Changes to this policy

This policy may be updated; changes will be published in the project repository with
an updated date above.

## Contact

Questions about this policy: **58160303+eizemazal@users.noreply.github.com**
(or open an issue at <https://github.com/eizemazal/avenarius/issues>).
