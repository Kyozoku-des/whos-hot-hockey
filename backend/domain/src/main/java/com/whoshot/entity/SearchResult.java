package com.whoshot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
public class SearchResult {

    @Id
    private Long id;
}
