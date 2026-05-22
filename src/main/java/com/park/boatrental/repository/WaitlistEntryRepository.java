package com.park.boatrental.repository;

import com.park.boatrental.model.WaitlistEntry;
import com.park.boatrental.waitlist.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByStatusInOrderByCreatedAtAsc(List<WaitlistStatus> statuses);

    List<WaitlistEntry> findByStatusOrderByCreatedAtAsc(WaitlistStatus status);

    @Query("SELECT COALESCE(MAX(e.queueNumber), 0) FROM WaitlistEntry e")
    int findMaxQueueNumber();
}
