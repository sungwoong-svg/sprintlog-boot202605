package com.sprintlog.sprintlogboot.repository;

import com.sprintlog.sprintlogboot.domain.WeeklyGoal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyGoalRepository extends JpaRepository<WeeklyGoal, Long> {

  Optional<WeeklyGoal> findByUserId(Long userId);

}
