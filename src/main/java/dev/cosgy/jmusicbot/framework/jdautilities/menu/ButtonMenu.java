package dev.cosgy.jmusicbot.framework.jdautilities.menu;

import dev.cosgy.jmusicbot.framework.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

public class ButtonMenu {
    private final String text;
    private final List<String> choices;
    private final EventWaiter waiter;
    private final long timeout;
    private final TimeUnit unit;
    private final Consumer<ButtonEvent> action;
    private final Consumer<Message> finalAction;

    private ButtonMenu(Builder builder) {
        this.text = builder.text;
        this.choices = builder.choices;
        this.waiter = builder.waiter;
        this.timeout = builder.timeout;
        this.unit = builder.unit;
        this.action = builder.action;
        this.finalAction = builder.finalAction;
    }

    public void display(MessageChannel channel) {
        channel.sendMessage(text == null ? "" : text).queue(this::activate);
    }

    public void display(Message message) {
        if (text == null) {
            activate(message);
            return;
        }
        message.editMessage(text).queue(this::activate, __ -> activate(message));
    }

    private void activate(Message message) {
        if (choices.isEmpty()) {
            if (finalAction != null) {
                finalAction.accept(message);
            }
            return;
        }

        Map<String, String> choiceById = new HashMap<>();
        List<Button> buttons = new ArrayList<>();
        String idPrefix = "btnmenu:" + message.getId();
        for (int i = 0; i < choices.size(); i++) {
            String choice = choices.get(i);
            String id = idPrefix + ":" + i;
            choiceById.put(id, choice);
            buttons.add(Button.secondary(id, choice));
        }

        message.editMessageComponents(ActionRow.of(buttons)).queue(updatedMessage -> {
            if (waiter == null || action == null) {
                return;
            }

            AtomicBoolean finished = new AtomicBoolean(false);
            waiter.waitForEvent(
                    ButtonInteractionEvent.class,
                    e -> e.getMessageIdLong() == updatedMessage.getIdLong()
                            && e.getUser() != null
                            && !e.getUser().isBot()
                            && choiceById.containsKey(e.getComponentId()),
                    e -> {
                        if (finished.compareAndSet(false, true)) {
                            String selected = choiceById.get(e.getComponentId());
                            e.deferEdit().queue();
                            action.accept(new ButtonEvent(selected));
                            if (finalAction != null) {
                                finalAction.accept(updatedMessage);
                            }
                        }
                    },
                    timeout,
                    unit,
                    () -> {
                        if (finished.compareAndSet(false, true) && finalAction != null) {
                            finalAction.accept(updatedMessage);
                        }
                    }
            );
        });
    }
    public static class ButtonEvent {
        private final String name;

        public ButtonEvent(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class Builder {
        private String text;
        private final List<String> choices = new ArrayList<>();
        private EventWaiter waiter;
        private long timeout = 1;
        private TimeUnit unit = TimeUnit.MINUTES;
        private Consumer<ButtonEvent> action;
        private Consumer<Message> finalAction;

        public Builder() {}

        public Builder setText(String text) {
            this.text = text;
            return this;
        }

        public Builder setChoices(String... choices) {
            this.choices.clear();
            if (choices != null) {
                for (String choice : choices) {
                    if (choice != null && !choice.isEmpty()) {
                        this.choices.add(choice);
                    }
                }
            }
            return this;
        }

        public Builder setEventWaiter(EventWaiter waiter) {
            this.waiter = waiter;
            return this;
        }

        public Builder setTimeout(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.unit = unit == null ? TimeUnit.MINUTES : unit;
            return this;
        }

        public Builder setAction(Consumer<ButtonEvent> action) {
            this.action = action;
            return this;
        }

        public Builder setFinalAction(Consumer<Message> finalAction) {
            this.finalAction = finalAction;
            return this;
        }

        public ButtonMenu build() {
            return new ButtonMenu(this);
        }
    }
}
