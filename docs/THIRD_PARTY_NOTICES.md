# Third-party notices

## sherpa-onnx v1.13.4

The packaged Android JNI runtime and Kotlin bindings are from
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), copyright its
contributors, and are licensed under Apache License 2.0. A copy of the
applicable license must accompany any released build.

## Bundled inference models

This development build bundles the following local model assets:

- `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01` for
  responsive partial transcription.
- `sherpa-onnx-paraformer-zh-small-2024-03-09` for final transcription.
- `silero_vad.onnx` for local voice activity detection.

These weights are independent artifacts with their own upstream terms. They
have been included for local development and evaluation only. Before a public
or commercial release, record the exact source URLs, hashes, and each model's
redistribution licence, then remove or replace any asset whose terms do not
permit the intended distribution.
