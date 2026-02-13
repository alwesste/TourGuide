package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.DTOS.AttractionLocation;
import com.openclassrooms.tourguide.DTOS.AttractionNearbyDTO;
import com.openclassrooms.tourguide.DTOS.UserLocation;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

@Service
public class TourGuideService {
    private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();
    public final Tracker tracker;
    boolean testMode = true;
    private final ExecutorService trackLocationExecutor;
    private int poolSize = 10;
//    int poolSize = Math.min(Runtime.getRuntime().availableProcessors() * 2, 50);

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;
        this.trackLocationExecutor = Executors.newFixedThreadPool(poolSize);
        logger.info("trackLocationExecutor initialisé avec {} threads", poolSize);

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }
        tracker = new Tracker(this);
        addShutDownHook();
    }

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation() : trackUserLocation(user);
        logger.debug(" Nom de User: {}, taille de visitedLocation: {}",
                user.getUserName(), user.getVisitedLocations().size());
        return visitedLocation;
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return internalUserMap.values().stream().collect(toList());
    }

    public void addUser(User user) {
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
        }
    }

    public List<Provider> getTripDeals(User user) {
        int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
        List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
        user.setTripDeals(providers);
        return providers;
    }

    // ecrire une methode qui prend une liste de users
    // inside mettre son pool de thread
    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        rewardsService.calculateRewards(user);
        return visitedLocation;
    }

    //CompletableyFuture
    public List<VisitedLocation> trackAllUserLocation(List<User> users) {
        logger.info("Début du tracking de {} utilisateurs", users.size());

        //Execute trackUserLocation avec le nombre de thread disponible dans poolsize
        List<CompletableFuture<VisitedLocation>> futures = users.stream()
                .map(user -> CompletableFuture.supplyAsync(() -> trackUserLocation(user), trackLocationExecutor))
                .toList();

        CompletableFuture<Void> everyFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));


        // v pour Void car allOf retourne un Void et thenApply a besoin d'une valeur en entre
        return everyFutures.thenApply(v -> {
            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        }).join();
    }


    /**
     * Récupère les 5 attractions les plus proches de la position actuelle de l'utilisateur.
     *  *
     *  * Pour chaque attraction disponible, calcule la distance par rapport à la dernière
     *  * localisation de l'utilisateur et les points de récompense associés.
     *  * Les attractions sont triées par distance croissante et seules les 5 premières sont retournées.
     * @param user l'utilisateur connecte dont on souhaite connaitre les attractions à proximite
     * @return une liste des 5 attractions les plus proches avec leurs informations
     */
    public List<AttractionNearbyDTO> getNearByAttractions(User user) {
        List<AttractionNearbyDTO> nearbyAttractions = new ArrayList<>();
        VisitedLocation visitedLocation = getUserLocation(user);

        for (Attraction attraction : gpsUtil.getAttractions()) {
            AttractionLocation attractionLocation = new AttractionLocation(
                    attraction.latitude,
                    attraction.longitude
            );
            UserLocation userLocation = new UserLocation(
                    visitedLocation.location.latitude,
                    visitedLocation.location.longitude
            );

            double distanceFromAttraction = rewardsService.getDistance(visitedLocation.location, attraction);
            int rewardPoint = rewardsService.getRewardPoints(attraction, user);

            AttractionNearbyDTO attractionNearby = new AttractionNearbyDTO(
                    attraction.attractionName,
                    attractionLocation,
                    userLocation,
                    distanceFromAttraction,
                    rewardPoint
            );

            nearbyAttractions.add(attractionNearby);

        }

        nearbyAttractions.sort(Comparator.comparingDouble(AttractionNearbyDTO::getDistanceFromAttraction));
        return nearbyAttractions.stream()
                .limit(5)
                .collect(toList());
    }


    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                tracker.stopTracking();
            }
        });
    }

    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";
    // Database connection will be used for external users, but for testing purposes
    // internal users are provided and stored in memory
    private final Map<String, User> internalUserMap = new HashMap<>();

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
        });
        logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
        });
    }

    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

}
