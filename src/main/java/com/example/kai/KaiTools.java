package com.example.kai;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

// Methods the model may call. The model only sees the name, description and
// parameters, so write descriptions as instructions for the model.
// Each call is logged so you can see in the IDE console when a tool is used.
@Component
public class KaiTools {

	private static final Logger log = LoggerFactory.getLogger(KaiTools.class);

	// Fake "database" for the demo
	private static final Map<String, String> ORDERS = Map.of(
			"A100", "Shipped on 25 Sep, arriving 30 Sep",
			"A200", "Processing, expected to ship 2 Oct",
			"A300", "Delivered on 20 Sep");

	private final List<String> reminders = new CopyOnWriteArrayList<>();

	// Try: "what time is it?"
	@Tool(description = "Get the current date and time in a timezone. Use for any question about the current time or date.")
	public String currentTime(
			@ToolParam(description = "IANA timezone, e.g. Asia/Singapore or America/New_York", required = false) String timezone) {
		String zone = (timezone == null || timezone.isBlank()) ? "Asia/Singapore" : timezone;
		log.info("TOOL currentTime({})", zone);
		return ZonedDateTime.now(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm z"));
	}

	// Try: "where is my order A100?" or "check orders A200 and A300"
	@Tool(description = "Look up the shipping status of an order by its order ID")
	public String orderStatus(@ToolParam(description = "Order ID, e.g. A100") String orderId) {
		log.info("TOOL orderStatus({})", orderId);
		return ORDERS.getOrDefault(orderId.trim().toUpperCase(), "No order found with ID " + orderId);
	}

	// Try: "remind me to call Alex tomorrow" then "what are my reminders?"
	@Tool(description = "Save a reminder for the user")
	public String addReminder(@ToolParam(description = "What to be reminded about") String text) {
		log.info("TOOL addReminder({})", text);
		reminders.add(text);
		return "Saved. The user now has " + reminders.size() + " reminder(s).";
	}

	@Tool(description = "List all reminders the user has saved")
	public List<String> listReminders() {
		log.info("TOOL listReminders()");
		return reminders;
	}
}
