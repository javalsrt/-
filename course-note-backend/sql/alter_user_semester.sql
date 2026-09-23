ALTER TABLE `course_note`.`user`
ADD COLUMN `semester` VARCHAR(64) DEFAULT '' COMMENT '当前学期' AFTER `grade`,
ADD COLUMN `semester_start_date` DATE DEFAULT NULL COMMENT '开学日期' AFTER `semester`,
ADD COLUMN `semester_weeks` INT DEFAULT 16 COMMENT '学期周数' AFTER `semester_start_date`;
