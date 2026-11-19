CREATE TABLE exams (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
 class_id BIGINT NOT NULL, exam_date DATE NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_exam_class FOREIGN KEY(class_id) REFERENCES classes(id)
);
CREATE TABLE scores (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL, exam_id BIGINT NOT NULL,
 subject VARCHAR(50) NOT NULL, score DECIMAL(10,2), full_score DECIMAL(10,2) NOT NULL,
 status VARCHAR(20) NOT NULL, remark VARCHAR(500) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_score UNIQUE(student_id,exam_id,subject),
 CONSTRAINT fk_score_student FOREIGN KEY(student_id) REFERENCES students(id),
 CONSTRAINT fk_score_exam FOREIGN KEY(exam_id) REFERENCES exams(id),
 CONSTRAINT ck_score CHECK(full_score>0 AND ((status='RECORDED' AND score IS NOT NULL AND score>=0 AND score<=full_score) OR (status='ABSENT' AND score IS NULL)))
);
CREATE TABLE assignments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, class_id BIGINT NOT NULL,
 subject VARCHAR(50) NOT NULL, description VARCHAR(2000) NOT NULL DEFAULT '', assigned_date DATE NOT NULL,
 deadline DATETIME NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_assignment_class FOREIGN KEY(class_id) REFERENCES classes(id)
);
CREATE TABLE assignment_records (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, assignment_id BIGINT NOT NULL, student_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', submitted_at DATETIME,
 remark VARCHAR(500) NOT NULL DEFAULT '', included BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_assignment_student UNIQUE(assignment_id,student_id),
 CONSTRAINT fk_record_assignment FOREIGN KEY(assignment_id) REFERENCES assignments(id),
 CONSTRAINT fk_record_student FOREIGN KEY(student_id) REFERENCES students(id),
 CONSTRAINT ck_assignment_status CHECK(status IN ('PENDING','COMPLETED','INCOMPLETE','LATE'))
);
CREATE TABLE attendance (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL, type VARCHAR(20) NOT NULL,
 occurred_date DATE NOT NULL, occurred_time TIME, remark VARCHAR(500) NOT NULL DEFAULT '',
 recorder VARCHAR(100) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_attendance_student FOREIGN KEY(student_id) REFERENCES students(id),
 CONSTRAINT ck_attendance_type CHECK(type IN ('LATE','EARLY_LEAVE','ABSENT'))
);
CREATE TABLE praises (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL, praise_date DATE NOT NULL,
 reason VARCHAR(500) NOT NULL, remark VARCHAR(500) NOT NULL DEFAULT '', recorder VARCHAR(100) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_praise_student FOREIGN KEY(student_id) REFERENCES students(id)
);
CREATE INDEX ix_exam_class_date ON exams(class_id,exam_date);
CREATE INDEX ix_assignment_class_date ON assignments(class_id,assigned_date);
CREATE INDEX ix_attendance_date_student ON attendance(occurred_date,student_id);
CREATE INDEX ix_praise_date_student ON praises(praise_date,student_id);
