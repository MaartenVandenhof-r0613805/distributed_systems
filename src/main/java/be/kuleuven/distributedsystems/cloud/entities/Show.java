package be.kuleuven.distributedsystems.cloud.entities;

import java.util.ArrayList;
import java.util.UUID;

public class Show {
    private String company;
    private UUID showId;
    private String name;
    private String location;
    private String image;
    private String timesLink;
    private String seatsLink;

    public Show() {}

    public Show(String company, UUID showId, String name, String location,
                String image, String timesLink, String seatsLink) {
        this.company = company;
        this.showId = showId;
        this.name = name;
        this.location = location;
        this.image = image;
        this.timesLink = timesLink;
        this.seatsLink = seatsLink;
    }

    public String getCompany() {
        return company;
    }

    public UUID getShowId() {
        return showId;
    }

    public String getName() {
        return this.name;
    }

    public String getLocation() {
        return this.location;
    }

    public String getImage() {
        return this.image;
    }

    public String getSeats() { return this.seatsLink;}

    public String getTimes() { return this.timesLink;}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Show)) {
            return false;
        }
        var other = (Show) o;
        return this.company.equals(other.company)
                && this.showId.equals(other.showId);
    }

    @Override
    public int hashCode() {
        return this.company.hashCode() * this.showId.hashCode();
    }
}
