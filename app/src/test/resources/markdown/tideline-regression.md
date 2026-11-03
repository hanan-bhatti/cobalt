# Tideline

This fixture reconstructs the reported Markdown cases from screenshots. It is not the original README.

## Quick start

```sh
curl -fsSL https://example.com/tideline/install.sh | sh
tideline add "Karachi Harbour" --id KHI-01
tideline today
```

Example output:

```text
Karachi Harbour (KHI-01)
Mon 3 Nov 2026

High  05:12   1.9 m
Low   11:34   0.4 m

High  17:48   2.1 m
Low   23:59   0.3 m
```

## Using it as a library

```go
package main

import (
    "fmt"
    "log"

    "github.com/example/tideline/pkg/tides"
)

func main() {
    client := tides.NewClient(tides.WithCache("~/.cache/tideline"))

    day, err := client.Today("KHI-01")

    if err != nil {
        log.Fatal(err)
    }

    fmt.Println(day)
}
```

## Data model

```kotlin
data class Station(val id: String, val name: String)

val station = Station("KHI-01", "Karachi Harbour")
```

```sql
CREATE TABLE station (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

SELECT * FROM station;
```

```json
{
  "station": "KHI-01",
  "highs": [
    {"time": "05:12", "height": 1.9}
  ]
}
```

## Benchmarks

| Operation | Cold start | Warm cache | Notes |
| --- | ---: | ---: | --- |
| `tideline today` | 220 ms | 12 ms | A wide final column that needs horizontal scrolling on a phone |
| `tideline export` | 60 ms | 9 ms | `<table|json|csv|md>` |

> **Tip**: Keep `--offline` enabled while travelling.
>
> More than one paragraph belongs in this quote.

> **Warning**: Never use sample tide data for navigation.

## Contributing

1. Clone the repository:

    ```sh
    git clone https://example.com/tideline

    cd tideline
    ```

2. Run the tests.

### Times look wrong

1. Check the station.
   - Check the timezone.
   - Check daylight saving time.
2. Refresh the cache.

## FAQ

**Does Tideline need an account?**

No. There is no sign-up and no account.

**Where is my data stored?**

In `~/.cache/tideline` on your own machine.

## License

Released under the MIT License.

Made with care. Write to hello@example.com or visit https://example.com/tideline.
