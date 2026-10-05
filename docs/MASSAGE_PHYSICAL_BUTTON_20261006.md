# 2026-10-06 Massage factory physical-button issue

Vehicle: Peugeot 5008 GT (2019); headunit: CMZXR62N-U1 / Raise PSA.
Owner reports that after ignition OFF and re-start, the factory seat massage physical button does not operate.

## Factory expected behavior
2019 owner manual: with the engine running, pressing front seat physical button should light green indicator and immediately activate last memorized massage settings; settings screen appears as well.
Reference: https://www.notice-facile.com/en/manual/1352717/peugeot%2B5008-2019
Official Peugeot 5008 earlier handbook confirms independent seat button activation plus UI: https://public.servicebox.peugeot.com/APddb/modeles/5008/eGuide_5008_5008n_ed01-17/pdfs/9999_9999_187_en-GB.pdf
Therefore loss of touchscreen menu alone does not adequately explain a nonfunctional physical button.

## Known V02 observation
Image at 2026-10-06 08:34:39 KST shows service and CAN conditions normal; passenger high/type1 command sent (0x85 07 31), 0x8F 4E query sent, before/after 0x4E cached value unchanged; V02 incorrectly labels this DIFF without proving fresh packet arrival.
More detail in MASSAGE_V02_REALCAR_ANALYSIS_20261006.md.

## Diagnostic priority
1. Ask whether button green LED illuminates after restart, when vehicle engine running.
2. Check whether problem affects both front seats, and whether it occurred before any test-app CAN commands.
3. Confirm test without app open / after full ignition cycle; do not send more commands until baseline reproduced.
4. V03 CAN receive-callback observer should be read-only when diagnosing physical button (no automatic writes/retries).
5. Do not classify V02 DIFF as a vehicle NACK. CANBox.write return is not ECU ACK.

## Status
Physical-button fault causality unresolved; do not deploy further massage control changes until LED and true receive-state results are known.
