# STEP 0 Vision Fixtures

These synthetic, non-sensitive images provide stable inputs for local Ollama vision benchmarks.

| File | Expected visible facts |
| --- | --- |
| `basque-cheesecake.png` | One Basque cheesecake slice, one white plate, one fork, light wooden table, no visible text |
| `iced-latte-croissant.png` | One iced latte in a clear glass, one croissant, one beige napkin, wooden table, no visible text |
| `cafe-interior.png` | Empty cafe interior, four wooden chairs, two round tables, one large potted plant, no visible text |

The fixtures were generated with the built-in image generation tool for benchmark use. They must not be treated as user content or as evidence of a real visit.

Run the default three-image batch:

```bash
./scripts/step0/benchmark-vision.sh gemma4:latest
```

Exercise the required image-count boundaries while keeping sequential batches of three:

```bash
VISION_IMAGE_COUNT=1 ./scripts/step0/benchmark-vision.sh gemma4:latest
VISION_IMAGE_COUNT=3 ./scripts/step0/benchmark-vision.sh gemma4:latest
VISION_IMAGE_COUNT=10 ./scripts/step0/benchmark-vision.sh gemma4:latest
VISION_IMAGE_COUNT=20 ./scripts/step0/benchmark-vision.sh gemma4:latest
```
