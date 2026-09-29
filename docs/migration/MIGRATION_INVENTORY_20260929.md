# Android_All migration inventory — 2026-09-29

Source repository: shj2564/PeugeotBackup
Destination repository: shj2564/Android_All

## Migrated active work
- Front camera R→D recovery lineage, including source snapshots and runnable artifacts through v18.2.
- Instrument cluster diagnostic/restore project and built APKs.
- Massage repair v01 source and built APK.
- Reverse mirror-dip recovery source, successful vehicle log, and analysis/guide material.
- CMZX / CarInfos / ReglinkService / Raise-PSA CAN analysis documents.

## Preserved history
history/legacy-branches/ contains file snapshots of the related historical development branches:
- build_frontcam-v11-source
- build_frontcam-v12-reversehold
- build_frontcam-v13-handlerprobe
- build_frontcam-v14-frontview-probe
- build_frontcam-v15-camera-collector
- build_frontcam-v16-oem-source2
- build_frontcam-v16-rear-source-hold
- build_frontcam-v17-controller-state-probe
- build_frontcam-v18-background-auto
- build_frontcam-v18-1-installfix
- build_frontcam-v18-2-parking-smooth
- cmzx-instrument-cluster-diag
- clustercheck-build-20260927
- massagecheck-build-20260929

## Excluded by design
Peugeot remote-start, OEM backup server, VPS, CY103 cellular/server research are kept in PeugeotBackup; they are not Android head-unit work.

## Source of truth
All new Android All-in-One development continues in this repository.
