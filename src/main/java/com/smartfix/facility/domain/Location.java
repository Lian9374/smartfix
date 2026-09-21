package com.smartfix.facility.domain;

import jakarta.persistence.*;

@Entity
@Table(
    name = "locations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_locations_location_code",
            columnNames = "location_code"
        )
    }
)
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_code", nullable = false, length = 50)
    private String locationCode;

    @Column(name = "building", length = 100)
    private String building;

    @Column(name = "floor", length = 20)
    private String floor;

    @Column(name = "room", length = 50)
    private String room;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Location() {
    }

    public Location(
        String locationCode,
        String building,
        String floor,
        String room,
        String displayName,
        boolean active
    ) {
        this.locationCode = locationCode;
        this.building = building;
        this.floor = floor;
        this.room = room;
        this.displayName = displayName;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public String getBuilding() {
        return building;
    }

    public String getFloor() {
        return floor;
    }

    public String getRoom() {
        return room;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }
}
