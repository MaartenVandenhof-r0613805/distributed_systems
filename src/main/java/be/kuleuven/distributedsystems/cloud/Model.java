package be.kuleuven.distributedsystems.cloud;

import be.kuleuven.distributedsystems.cloud.entities.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class Model {
    private final WebClient reliableClient = WebClient.create("https://reliabletheatrecompany.com");
    private final WebClient unreliableClient = WebClient.create("https://unreliabletheatrecompany.com");
    private final String apiKey = "key=wCIoTqec6vGJijW2meeqSokanZuqOL";
    private ArrayList<Booking> bookings = new ArrayList<>();


    public List<Show> getShows() {
        // Get JSON
        String response = reliableClient.get()
                .uri("/shows/?key=wCIoTqec6vGJijW2meeqSokanZuqOL")
                .retrieve()
                .bodyToMono(String.class).block();

        ArrayList<String> responseList = new ArrayList<>();
        responseList.add(response);

        try {
            String responseUnreliable = unreliableClient.get()
                    .uri("/shows/?key=wCIoTqec6vGJijW2meeqSokanZuqOL")
                    .retrieve()
                    .bodyToMono(String.class).block();
            responseList.add(responseUnreliable);
        } catch (Exception e) {
            System.out.println("Unreliable company not added");
        }

        // Create list that will contains all shows
        ArrayList<Show> shows = new ArrayList<>();
        // Get data from JSON
        try {
            for (String r : responseList) {
                JsonNode jsonNode = new ObjectMapper().readTree(r);
                for (JsonNode objNode : jsonNode.get("_embedded").get("shows")) {

                    UUID uuid = UUID.fromString(objNode.get("showId").toString().substring(1, 36));
                    JsonNode links = objNode.get("_links");
                    shows.add(
                            new Show(
                                    objNode.get("company").toString().replace("\"", ""),
                                    uuid, objNode.get("name").toString().replace("\"", ""),
                                    objNode.get("location").toString().replace("\"", ""),
                                    objNode.get("image").toString().replace("\"", ""),
                                    links.get("times").get("href").toString().replace("\"", ""),
                                    links.get("seats").get("href").toString().replace("\"", ""))
                    );
                }
            }
        } catch (Exception e){
            System.out.println(e);
            System.out.println(e.getMessage());
        }
        return shows;
    }

    public Show getShow(String company, UUID showId) {
        for (Show show : this.getShows()) {
            if (show.getCompany().equals(company) && showId.equals(show.getShowId())) {
                return show;
            }
        }
        return null;
    }

    public List<LocalDateTime> getShowTimes(String company, UUID showId) {
        // Get api link from show
        Show show = this.getShow(company, showId);
        String apiLink = show.getTimes();

        // Get JSON
        String response = reliableClient.get()
                .uri(apiLink + "?" + apiKey)
                .retrieve()
                .bodyToMono(String.class).block();

        ArrayList<String> responseList = new ArrayList<>();
        responseList.add(response);

        try {
            String unreliableResponse = unreliableClient.get()
                    .uri(apiLink + "?" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class).block();
            responseList.add(unreliableResponse);
        } catch (Exception e) {
            System.out.println("Unreliable company not added");
        }

        // Get times from JSON
        ArrayList<LocalDateTime> times = new ArrayList<>();

        try {
            for (String r : responseList) {
                JsonNode node = new ObjectMapper().readTree(r).get("_embedded").get("stringList");
                for (JsonNode n : node) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                    LocalDateTime time = LocalDateTime.parse(n.toString().replace("\"", ""), formatter);
                    times.add(time);
                }
            }
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(e.getMessage());
        }
        return times;
    }

    private List<Seat> getSeatsGivenParameters(String company, UUID showId, LocalDateTime time, String apiLink) {
        // Get JSON
        String response = reliableClient.get()
                .uri(apiLink + "&" + apiKey)
                .retrieve()
                .bodyToMono(String.class).block();

        ArrayList<String> responseList = new ArrayList<>();
        responseList.add(response);

        try {
            String unreliableResponse = unreliableClient.get()
                    .uri(apiLink + "?" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class).block();
            responseList.add(unreliableResponse);
        } catch (Exception e) {
            System.out.println("Unreliable company not added");
        }
        // Get available seats from JSON
        ArrayList<Seat> seats = new ArrayList<>();
        try {
            for (String r : responseList) {
                JsonNode node = new ObjectMapper().readTree(r).get("_embedded").get("seats");
                for (JsonNode n : node) {
                    UUID seatId = UUID.fromString(n.get("seatId").toString().substring(1, 36));
                    JsonNode links = n.get("_links");
                    Seat seat = new Seat(
                            company,
                            showId,
                            seatId,
                            time,
                            n.get("type").toString().replace("\"", ""),
                            n.get("name").toString().replace("\"", ""),
                            Float.parseFloat(n.get("price").toString().replace("\"", "")),
                            links.get("get-ticket").get("href").toString().replace("\"", ""),
                            links.get("put-ticket").get("href").toString().replace("\"", "")
                    );
                    seats.add(seat);
                }
            }
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(e.getMessage());
        }
        return seats;
    }

    public List<Seat> getAvailableSeats(String company, UUID showId, LocalDateTime time) {
        // Get api link from show
        Show show = this.getShow(company, showId);
        String apiLink = show.getSeats();
        String apiLinkTime = apiLink.replace("{time}", time.toString());

        return this.getSeatsGivenParameters(company, showId, time, apiLinkTime);
    }

    public List<Seat> getUnavailableSeats(String company, UUID showId, LocalDateTime time) {
        // Get api link from show
        Show show = this.getShow(company, showId);
        String apiLink = show.getSeats();
        String apiLinkTime = apiLink.replace("{time}", time.toString())
                .replace("available=True", "available=False");

        return this.getSeatsGivenParameters(company, showId, time, apiLinkTime);
    }

    public Seat getSeat(String company, UUID showId, UUID seatId) {
        ArrayList<LocalDateTime> times = (ArrayList<LocalDateTime>) this.getShowTimes(company, showId);
        for (LocalDateTime time : times) {
            for (Seat seat : this.getAvailableSeats(company, showId, time)) {
                if (seat.getSeatId().equals(seatId)) return seat;
            }

            for (Seat seat : this.getUnavailableSeats(company, showId, time)) {
                if (seat.getSeatId().equals(seatId)) return seat;
            }
        }
        return null;
    }

    public Ticket getTicket(String company, UUID showId, UUID seatId) {
        // Get ticket link
        String getTicketLink = this.getSeat(company, showId, seatId).getGetTicketLink();

        try {
            // Get JSON
            String response = reliableClient.get()
                    .uri(getTicketLink + "?" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class).block();

            ArrayList<String> responseList = new ArrayList<>();
            responseList.add(response);

            try {
                String unreliableResponse = unreliableClient.get()
                        .uri(getTicketLink + "?" + apiKey)
                        .retrieve()
                        .bodyToMono(String.class).block();
                responseList.add(unreliableResponse);
            } catch (Exception e) {
                System.out.println("Unreliable company not added");
            }

            // Create ticket object
            for (String r : responseList) {
                JsonNode node = new ObjectMapper().readTree(r);
                UUID uuid = UUID.fromString(node.get("ticketId").toString().substring(1, 36));
                return new Ticket(
                        node.get("company").toString().replace("\"", ""),
                        showId,
                        seatId,
                        uuid,
                        node.get("company").toString().replace("\"", ""));
            }
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(e.getMessage());
            return null;
        }
        return null;
    }

    public List<Booking> getBookings(String customer) {
        ArrayList<Booking> customerBookings = new ArrayList<>();
        for (Booking booking : this.bookings) {
            if (booking.getCustomer().equals(customer)) customerBookings.add(booking);
        }
        return customerBookings;
    }

    public List<Booking> getAllBookings() {
        return bookings;
    }

    public Set<String> getBestCustomers() {
        // Get customers and their ticket amounts
        HashMap<String, Integer> customerTicketAmount = new HashMap<>();
        for (Booking booking : this.bookings) {
            String customer = booking.getCustomer();
            if (!customerTicketAmount.containsKey(customer)) {
                customerTicketAmount.put(customer, 0);
            } else {
                int oldVal = customerTicketAmount.get(customer);
                customerTicketAmount.replace(customer, oldVal + 1);
            }
        }

        // Select best customers based on their ticket amounts
        ArrayList<String> bestCustomers = new ArrayList<>();
        bestCustomers.add(this.bookings.get(0).getCustomer());
        int amount = customerTicketAmount.get(this.bookings.get(0).getCustomer());
        for (String key : customerTicketAmount.keySet()) {
            if (!key.equals(bestCustomers.get(0))) {
                if (customerTicketAmount.get(key) > amount) {
                    amount = customerTicketAmount.get(key);
                    bestCustomers = new ArrayList<>();
                    bestCustomers.add(key);
                }
                if (customerTicketAmount.get(key) == amount) bestCustomers.add(key);
            }
        }

        // Return best customers
        return new HashSet<>(bestCustomers);
    }

    public void confirmQuotes(List<Quote> quotes, String customer) throws InvalidReservationException {
        ArrayList<Ticket> tickets = new ArrayList<>();

        // Check if ticket is reservable
        for (Quote quote : quotes) {
            // Check if ticket exists
            Ticket ticket = this.getTicket(quote.getCompany(), quote.getShowId(), quote.getSeatId());
            if (ticket != null) throw new InvalidReservationException("Ticket already reserved, booking cancelled.");
        }

        // Reserve ticket
        for (Quote quote : quotes) {
            // Create ticket and add to ticket list
            Ticket ticket = new Ticket(quote.getCompany(), quote.getShowId(),
                    quote.getSeatId(), UUID.randomUUID(), customer);

            // Add tickets to list
            tickets.add(ticket);

            // Create seat object to get reservation link
            Seat seat = this.getSeat(quote.getCompany(), quote.getShowId(), quote.getSeatId());

            // Confirm ticket reservation
            try {
                reliableClient.put()
                        .uri(seat.getPutTicketLink() + customer + "&" + apiKey)
                        .retrieve()
                        .bodyToMono(String.class);
            } catch (Exception e) {
                try {
                    System.out.println(e);
                    unreliableClient.put()
                            .uri(seat.getPutTicketLink() + customer + "&" + apiKey)
                            .retrieve()
                            .bodyToMono(String.class);
                } catch (Exception ex) {
                    System.out.println(ex);
                    return;
                }
            }
        }
        // Create and save booking
        Booking booking = new Booking(UUID.randomUUID(), LocalDateTime.now(), tickets, customer);
        this.bookings.add(booking);
    }
}
