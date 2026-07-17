package com.validdoc.repository;

import com.validdoc.model.ValidationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidationSettingsRepository extends JpaRepository<ValidationSettings, Long> {
}