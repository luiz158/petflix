package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.function.wiretap.Bridge;
import org.springframework.cloud.function.wiretap.DefaultBridge;
import org.springframework.context.annotation.Bean;

import reactor.core.publisher.Flux;

@SpringBootApplication
public class CommanderApplication {

    private List<Command> commands = new ArrayList<>();

    private Map<String, Command> commandsById = new HashMap<>();

    private List<Event> events = new ArrayList<>();

    private Map<String, Event> eventsById = new HashMap<>();

    @Autowired
    private Bridge<Command> commandBridge;

    @Autowired
    private Bridge<Event> eventBridge;

    @Bean
    protected Bridge<Command> commandBridge() {
        return new DefaultBridge<>();
    };

    @Bean
    protected Bridge<Event> eventBridge() {
        return new DefaultBridge<>();
    }

    @Bean
    public Consumer<Flux<Command>> commands() {
        return commands -> commands.subscribe(command -> {
            if (commandsById.computeIfAbsent(command.getId(), id -> command) != null) {
                commandBridge.send(command);
                this.commands.add(command);
            }
        });
    }

    @Bean
    @Qualifier("commands")
    public Function<Flux<String>, Flux<Command>> storeCommands() {
        return ids -> ids.map(id -> commandsById.get(id));
    }

    @Bean
    @Qualifier("commands")
    public Supplier<Flux<Command>> replayCommands() {
        return () -> Flux.fromIterable(commands);
    }

    @Bean
    public Supplier<Flux<Command>> exportCommands(Bridge<Command> commandBridge) {
        return () -> commandBridge.receive();
    }

    @Bean
    public Consumer<Flux<Event>> events() {
        return events -> events.filter( //
                event -> eventsById.containsKey(event.getParent())
                        || commandsById.containsKey(event.getParent()) //
        ).subscribe(event -> {
            if (eventsById.computeIfAbsent(event.getId(), id -> event) != null) {
                eventBridge.send(event);
                this.events.add(event);
            }
        });
    }

    @Bean
    @Qualifier("events")
    public Function<Flux<String>, Flux<Event>> storeEvents() {
        return ids -> ids.map(id -> eventsById.get(id));
    }

    @Bean
    @Qualifier("events")
    public Supplier<Flux<Event>> replayEvents() {
        return () -> Flux.fromIterable(events);
    }

    @Bean
    public Supplier<Flux<Event>> exportEvents(Bridge<Event> eventBridge) {
        return () -> eventBridge.receive();
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(CommanderApplication.class).run(args);
    }
}
