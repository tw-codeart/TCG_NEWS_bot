# QQ card images and unverified OCR

Each newly saved card image can be delivered as a merged forward containing two
nodes: metadata from the existing formatter, then the original image and raw OCR
text. Missing-image news uses plain text. QQ text omits HTTP(S) and `www.` links;
image URLs are transport metadata. Stored official URLs are unchanged. This does
not guarantee that QQ will accept messages or avoid account restrictions.

## Configuration

QQ remains disabled by default. Legacy/manual previews use the configured test
group; opt-in [group subscriptions](qq-group-subscriptions.md) allow trusted group routing.
When QQ is enabled, `qq.image-forward-enabled` and `qq.ocr-enabled` default to
true. Environment variables are `QQ_IMAGE_FORWARD_ENABLED` and `QQ_OCR_ENABLED`.
Disabling OCR keeps the image and adds a disabled notice. Explicitly disabling
image forwarding restores legacy plain text, including source links.

Only original HTTPS images on `ws-tcg.com`, its subdomains or `pbs.twimg.com`
are accepted. Java does not download, transform or permanently store images.
NapCat obtains them at runtime. Its OCR action removes downloaded temporary
inputs; QQ delivery may still use NapCat's normal runtime cache. SAMPLE marks
and watermarks are not modified.

## OCR and delivery limits

`CardTextExtractor` uses NapCat's Windows `ocr_image` action. Returned lines are
marked unverified. There is no guessing of missing names/rules, translation,
automatic correction or separation of effect text from other card text. Japanese
coverage, reading order and card-text accuracy need live verification. Mocked
Japanese responses do not prove recognition accuracy.

Failed, empty, malformed or timed-out OCR still permits sending the original image
with an explicit fallback. Failed forwards are reported without rolling back saved
news. Forward nodes use the logged-in bot's identity from `get_login_info`.

Existing first-run history suppression remains. Automatic image delivery groups
distinct records into bounded packets, default three attempts with a hard maximum
of five per fetch. Failed/limited records remain pending for later checks.
See [automatic delivery](automatic-qq-delivery.md). Manual `/news` preview still
selects individual records; `/posts` preview selects whole stored posts.

Manual preview uses `POST /api/notifications/news?limit=1`. Fixed smoke messages
from `POST /api/notifications/test` also omit the old official link.

All HTTP tests use synthetic endpoints and mocked responses. Live Japanese OCR,
image receipt, merged-forward receipt and laptop installation are not verified.
Group subscription commands restricted to owners/admins are available as an
opt-in; see [group subscriptions](qq-group-subscriptions.md).

## References

- https://napneko.github.io/onebot/segment
- https://napneko.github.io/onebot/api
- https://github.com/NapNeko/NapCatQQ/blob/v4.18.33/packages/napcat-onebot/action/extends/OCRImage.ts
- https://github.com/NapNeko/NapCatQQ/blob/v4.18.33/packages/napcat-onebot/action/go-cqhttp/SendForwardMsg.ts
