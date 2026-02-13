package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
	private Logger logger = LoggerFactory.getLogger(RewardsService.class);
	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = Integer.MAX_VALUE;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ExecutorService calculateRewardExecutor;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		int poolSize = 50;
        this.calculateRewardExecutor = Executors.newFixedThreadPool(poolSize);
		logger.info("calculateRewardExecutor initialisé avec {} threads", poolSize);

	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	public void calculateRewards(User user) {

		// Ajout d'un Array pour evite le ConcurrentModificationException
		List<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();

		for(VisitedLocation visitedLocation : userLocations) {
			for(Attraction attraction : attractions) {
				if(user.getUserRewards().stream()
						.filter(r -> r.attraction.attractionName.equalsIgnoreCase(attraction.attractionName))
						.count() == 0) {
					if(nearAttraction(visitedLocation, attraction)) {
						user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
					}
				}
			}
		}
	}

	public void calculateRewardsFast(List<User> users) {
		List<CompletableFuture<Void>> futures = users.stream()
				.map(user -> CompletableFuture.runAsync(() -> calculateRewards(user), calculateRewardExecutor))
				.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}



	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		System.out.println("Distance :" + getDistance(attraction, location));
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
		return statuteMiles;
	}

}
