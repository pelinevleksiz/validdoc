package com.validdoc.repository;

import com.validdoc.model.Template;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {
    Page<Template> findByActiveTrue(Pageable pageable);

    boolean existsByNameAndActiveTrue(String name);
}