package com.whoshot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Minimal entity projection mapped to the players table for search queries.
 */
@Entity
@Table(name = "players")
public class SearchResult {

    @Id
    private Long id;
}
