package dev.cosgy.jmusicbot.framework.jdautilities.menu;

import dev.cosgy.jmusicbot.framework.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

public class ButtonMenu {
    private final String text;
    private final Consumer<ButtonEvent> action;
    private final Consumer<Message> finalAction;

    private ButtonMenu(Builder builder) {
        this.text = builder.text;
        this.action = builder.action;
        this.finalAction = builder.finalAction;
    }

    public void display(MessageChannel channel) {
        channel.sendMessage(text == null ? "" : text).queue(message -> {
            if (action != null) {
                // Fallback behavior for the lightweight fork framework: auto-select first choice.
                action.accept(new ButtonEvent("1"));
            }
            if (finalAction != null) {
                finalAction.accept(message);
            }
        });
    }

    public void display(Message message) {
        display(message.getChannel());
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
        private Consumer<ButtonEvent> action;
        private Consumer<Message> finalAction;

        public Builder() {}

        public Builder setText(String text) {
            this.text = text;
            return this;
        }

        public Builder setChoices(String... ignored) {
            return this;
        }

        public Builder setEventWaiter(EventWaiter ignored) {
            return this;
        }

        public Builder setTimeout(long ignoredTimeout, TimeUnit ignoredUnit) {
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
