# Westarps Vaadin Animate

Reusable Animate.css effects and lifecycle helpers for Vaadin components.

Add the bundled stylesheet to a component that uses animations:

```java
@CssImport(Animations.STYLESHEET)
public class ExampleComponent extends Div {
}
```

Play an effect once and remove its classes automatically when it ends:

```java
Animations.playOnce(component, Effect.HEAD_SHAKE, Speed.FASTER);
```

Start and stop a repeated effect:

```java
Animations.start(component, Effect.BOUNCE, Repeat.INFINITE, Speed.FAST);
Animations.stop(component);
```

Starting an animation removes any animation classes previously managed by this
module without disturbing unrelated component classes. Animate.css effects use
the CSS `transform` property, so components that need transform-based layout
should animate a child or move their layout positioning to independent CSS
properties such as `translate`.
