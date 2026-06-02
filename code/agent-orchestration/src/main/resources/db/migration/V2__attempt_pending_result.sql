-- FE3：attempt 终态乱序合并所需的暂存字段。见详细设计 §8.4。
-- attempt.result（语义结果）与 runtime.*（物理终态）可乱序到达，先到者暂存，两者齐再定终态。

ALTER TABLE step_attempt ADD COLUMN pending_result_status  VARCHAR(20);   -- SUCCEEDED/FAILED（来自 attempt.result）
ALTER TABLE step_attempt ADD COLUMN pending_result_summary TEXT;
ALTER TABLE step_attempt ADD COLUMN pending_result_detail  TEXT;
ALTER TABLE step_attempt ADD COLUMN pending_session_ref    VARCHAR(256);
ALTER TABLE step_attempt ADD COLUMN runtime_terminal       VARCHAR(20);   -- COMPLETED/FAILED/CANCELLED（来自 runtime.*）
