# Westarps Vaadin Badge

Reusable Vaadin badges and the small framework used to attach a badge to a
component after that component has been created.

Vaadin 25 loads the Lumo badge styles automatically. No application-theme
configuration is required.

Implement `Badgeable` when a component needs access to its badge controller:

```java
public final class NotificationsButton extends Button
        implements Badgeable<NotificationsButton> {

    private BadgeController badgeController;

    @Override
    public void onBadged(final BadgeController badgeController) {
        this.badgeController = badgeController;
    }
}
```

The component can be wrapped explicitly with `BadgedComponent`, or conditionally
with `Badgeable.badgeIfPossible(...)`. The controller shows text or icon badges,
sets their tooltip, and hides them immediately or after a delay.
