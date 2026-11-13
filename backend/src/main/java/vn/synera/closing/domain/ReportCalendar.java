package vn.synera.closing.domain;

import jakarta.persistence.*;

/** Shared lock for postings; exclusive lock for period creation and closing. */
@Entity
@Table(name = "report_calendar")
public class ReportCalendar {
  @Id public Long id;
}
