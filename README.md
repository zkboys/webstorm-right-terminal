# Right Terminal

Local WebStorm plugin that adds a `Right Terminal` tool window on the right stripe.

## Build

```bash
./scripts/build-plugin.sh
```

The installable zip is written to `build/distributions/right-terminal.zip`.

## Install Locally

```bash
./scripts/install-local.sh
```

Restart WebStorm after installing. The right stripe will show a terminal icon named `Right Terminal`; clicking it opens a terminal pane on the right side.

If WebStorm is not installed at `/Applications/WebStorm.app`, run with:

```bash
WEBSTORM_APP="/path/to/WebStorm.app" ./scripts/install-local.sh
```
