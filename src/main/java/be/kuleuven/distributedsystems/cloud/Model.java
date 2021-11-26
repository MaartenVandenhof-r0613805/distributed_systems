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

        String responseUnreliable = unreliableClient.get()
                .uri("/shows/?key=wCIoTqec6vGJijW2meeqSokanZuqOL")
                .retrieve()
                .bodyToMono(String.class).block();

        // Create list that will contains all shows
        ArrayList<Show> shows = new ArrayList<>();
        // Get data from JSON
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(response);
            for (JsonNode objNode : jsonNode.get("_embedded").get("shows")) {

                UUID uuid = UUID.fromString(objNode.get("showId").toString().substring(1, 36));
                JsonNode links = objNode.get("_links");
                shows.add(
                        new Show(
                                objNode.get("company").toString(),
                                uuid, objNode.get("name").toString(),
                                objNode.get("location").toString(),
                                objNode.get("image").toString(),
                                links.get("times").get("href").toString(),
                                links.get("seats").get("href").toString())
                );
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
                .uri(apiLink + "&" + apiKey)
                .retrieve()
                .bodyToMono(String.class).block();

        // Get times from JSON
        ArrayList<LocalDateTime> times = new ArrayList<>();

        try {
            JsonNode node = new ObjectMapper().readTree(response).get("_embedded").get("stringList");
            for (JsonNode n : node) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime time = LocalDateTime.parse(n.toString(), formatter);
                times.add(time);
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

        // Get available seats from JSON
        ArrayList<Seat> seats = new ArrayList<>();
        try {
            JsonNode node = new ObjectMapper().readTree(response).get("_embedded").get("seats");
            for (JsonNode n : node) {
                UUID seatId = UUID.fromString(n.get("seatId").toString().substring(1, 36));
                JsonNode links = n.get("_links");
                Seat seat = new Seat(
                        n.get("company").toString(),
                        showId,
                        seatId,
                        time,
                        n.get("type").toString(),
                        n.get("name").toString(),
                        Integer.parseInt(n.get("price").toString()),
                        links.get("get-ticket").get("href").toString(),
                        links.get("put-ticket").get("href").toString()
                );
                seats.add(seat);
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

        // Get JSON
        String response = reliableClient.get()
                .uri(getTicketLink + "?" + apiKey)
                .retrieve()
                .bodyToMono(String.class).block();

        // Create ticket object
        try {
            JsonNode node = new ObjectMapper().readTree(response);
            UUID uuid = UUID.fromString(node.get("ticketId").toString().substring(1, 36));
            return new Ticket(
                    node.get("company").toString(),
                    showId,
                    seatId,
                    uuid,
                    node.get("company").toString()
            );
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(e.getMessage());
            return null;
        }
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
            if (ticket == null) throw new InvalidReservationException("Ticket already reserved, booking cancelled.");
        }

        // Reserve ticket
        for (Quote quote : quotes) {
            // Create ticket and add to ticket list
            Ticket ticket = new Ticket(quote.getCompany(), quote.getShowId(),
                    quote.getSeatId(), UUID.randomUUID(), customer);

            // Create seat object to get reservation link
            Seat seat = this.getSeat(quote.getCompany(), quote.getShowId(), quote.getSeatId());
            // Confirm ticket reservation
            reliableClient.put()
                    .uri(seat.getPutTicketLink() + customer + "&" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class);
        }

        // Create and save booking
        Booking booking = new Booking(UUID.randomUUID(), LocalDateTime.now(), tickets, customer);
        this.bookings.add(booking);
    }
}
