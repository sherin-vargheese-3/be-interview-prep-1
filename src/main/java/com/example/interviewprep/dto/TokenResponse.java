package com.example.interviewprep.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	@Override
	public String toString() {
		return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
	}
}
