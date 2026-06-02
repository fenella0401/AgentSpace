-- FE 修复 #7：暂存 attempt.result 的 artifactRefs，供乱序合并后写入 step。见 §7.2、§8.4。
ALTER TABLE step_attempt ADD COLUMN pending_artifact_refs TEXT;
