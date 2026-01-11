package com.openclassrooms.tourguide.DTOS;

public class AttractionNearbyDTO {
    private String attractionName;
    private AttractionLocation attractionLocation;
    private UserLocation userLocation;
    private double distanceFromAttraction;
    private Integer rewardPoint;

    public AttractionNearbyDTO(String attractionName, AttractionLocation attractionLocation, UserLocation userLocation, double distanceFromAttraction, Integer rewardPoint) {
        this.attractionName = attractionName;
        this.attractionLocation = attractionLocation;
        this.userLocation = userLocation;
        this.distanceFromAttraction = distanceFromAttraction;
        this.rewardPoint = rewardPoint;
    }

    public AttractionNearbyDTO() {

    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public AttractionLocation getAttractionLocation() {
        return attractionLocation;
    }

    public void setAttractionLocation(AttractionLocation attractionLocation) {
        this.attractionLocation = attractionLocation;
    }

    public UserLocation getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(UserLocation userLocation) {
        this.userLocation = userLocation;
    }

    public double getDistanceFromAttraction() {
        return distanceFromAttraction;
    }

    public void setDistanceFromAttraction(double distanceFromAttraction) {
        this.distanceFromAttraction = distanceFromAttraction;
    }

    public Integer getRewardPoint() {
        return rewardPoint;
    }

    public void setRewardPoint(Integer rewardPoint) {
        this.rewardPoint = rewardPoint;
    }
}
