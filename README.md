# libflac-java

An unofficial pure Java port of [libFLAC](https://github.com/xiph/flac), supporting FLAC encoding and decoding, metadata editing, and ReplayGain analysis and synthesis.

## Features

- FLAC encoding and decoding
- Ogg FLAC encoding and decoding through JOrbis
- Metadata objects, quick metadata reads, simple iterators and metadata chains
- ReplayGain analysis, gain application, dithering and noise shaping
- File and callback APIs following the upstream C library
- Separate encoding and decoding examples

## Releases

### Gradle/Maven

Available versions are listed on [GitHub Releases](https://github.com/RaphiMC/libflac-java/releases). Replace `VERSION` in the examples below with your chosen release version. The first three version components identify the upstream release; the fourth is the Java port revision.

Gradle:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.raphimc:libflac-java:VERSION'
}
```

Maven:

```xml
<dependency>
    <groupId>io.github.raphimc</groupId>
    <artifactId>libflac-java</artifactId>
    <version>VERSION</version>
</dependency>
```

### Optional Ogg support

JOrbis is optional and is not included transitively. If you need Ogg FLAC encoding or decoding, add `org.jcraft:jorbis` to your runtime dependencies yourself. Native FLAC operations, including metadata handling, and ReplayGain do not require it.

Use the JOrbis version declared in your chosen release's [build.gradle](build.gradle) as `JORBIS_VERSION`:

Gradle:

```groovy
dependencies {
    implementation 'org.jcraft:jorbis:JORBIS_VERSION'
}
```

Maven:

```xml
<dependency>
    <groupId>org.jcraft</groupId>
    <artifactId>jorbis</artifactId>
    <version>JORBIS_VERSION</version>
</dependency>
```

### Jar File

To build the library, sources and Javadoc JARs locally, run:

```sh
./gradlew build
```

The JARs are generated in `build/libs/`. If you use Ogg support without a dependency manager, include the JOrbis JAR on the classpath yourself.

## Usage

`StreamEncoder` and `StreamDecoder` provide the codec APIs. Metadata operations are available through `Metadata`, `MetadataSimpleIterator`, `MetadataChain` and `MetadataIterator`.

### Encoding PCM

This example encodes one second of 16-bit stereo silence at 44.1 kHz. Interleaved PCM stores each sample's channels consecutively; the sample count passed to `processInterleaved` is per channel.

```java
import io.github.raphimc.libflac.StreamEncoder;
import io.github.raphimc.libflac.StreamEncoderInitStatus;
import java.io.IOException;

int channels = 2;
int[] pcm = new int[44_100 * channels];

try (StreamEncoder encoder = new StreamEncoder()) {
    encoder.setChannels(channels);
    encoder.setBitsPerSample(16);
    encoder.setSampleRate(44_100);
    encoder.setCompressionLevel(5);

    StreamEncoderInitStatus status = encoder.initFile("example.flac", null);
    if (status != StreamEncoderInitStatus.OK)
        throw new IOException("Encoder initialization failed: " + status);
    if (!encoder.processInterleaved(pcm, pcm.length / channels))
        throw new IOException("Encoding failed: " + encoder.getResolvedStateString());
    encoder.finish();
}
```

Call `finish()` to write the final samples and metadata before closing the encoder. Use `process` for planar PCM, or `initStream` to supply your own I/O callbacks. The corresponding `initOggFile` and `initOggStream` operations produce Ogg FLAC.

### Decoding FLAC

The write callback receives planar PCM as `buffer[channel][sample]`. The frame header describes the current block's sample rate, channel count, bit depth and sample count.

```java
import io.github.raphimc.libflac.StreamDecoder;
import io.github.raphimc.libflac.StreamDecoderInitStatus;
import java.io.IOException;

try (StreamDecoder decoder = new StreamDecoder()) {
    decoder.setMd5Checking(true);
    StreamDecoderInitStatus status = decoder.initFile("example.flac", (d, frame, buffer) -> {
        System.out.println("Decoded " + frame.getHeader().getBlocksize() + " samples per channel");
        return true;
    }, null, (d, error) -> {
        throw new IllegalStateException("Decoder error: " + error);
    });
    if (status != StreamDecoderInitStatus.OK)
        throw new IOException("Decoder initialization failed: " + status);
    if (!decoder.processUntilEndOfStream())
        throw new IOException("Decoding failed: " + decoder.getResolvedStateString());
    if (!decoder.finish())
        throw new IOException("FLAC MD5 verification failed");
}
```

This example stops on decoder diagnostics. Returning `false` from the write callback requests an abort. Use `initStream` for callback input or the `initOgg*` operations for Ogg FLAC.

### Reading metadata

For a quick STREAMINFO read:

```java
import io.github.raphimc.libflac.Metadata;
import io.github.raphimc.libflac.StreamInfo;

StreamInfo info = Metadata.getStreamInfo("example.flac");
if (info != null)
    System.out.println("Sample rate: " + info.getSampleRate());
```

`Metadata` also reads Vorbis comments, cue sheets and pictures. Use `MetadataSimpleIterator` for edits to individual blocks, or `MetadataChain` with `MetadataIterator` to edit the complete metadata chain.

### File examples

The [examples module](examples/src/main/java/io/github/raphimc/libflac/examples) contains the translated upstream file examples:

```sh
./gradlew :examples:encode --args='input.wav output.flac'
./gradlew :examples:decode --args='output.flac restored.wav'
```

These examples retain upstream's format restrictions: encoding accepts canonical 16-bit stereo PCM WAVE, and decoding expects 16-bit stereo FLAC with a known sample count. The library supports other FLAC formats through its codec APIs.

### Upstream sources

The upstream C sources are included through the pinned [src_upstream](src_upstream) Git submodule. They are only needed when maintaining or comparing the port.

Clone the repository with its submodules:

```sh
git clone --recurse-submodules https://github.com/RaphiMC/libflac-java.git
```

For an existing checkout, initialize the submodule with:

```sh
git submodule update --init --recursive
```

## AI disclosure

The upstream libFLAC C code was translated to Java with extensive AI assistance. Human review and feedback guided the work.

## License

The port retains upstream's component licenses:

| Component | License |
| --- | --- |
| Codec, metadata and Ogg integration | [BSD-3-Clause](COPYING.Xiph) |
| ReplayGain analysis and synthesis | [LGPL-2.1-or-later](COPYING.LGPL) |
| Translated examples and tests | [GPL-2.0-or-later](COPYING.GPL) |

## Contact

If you encounter any issues specific to this Java port of libFLAC, please report them on the [issue tracker](https://github.com/RaphiMC/libflac-java/issues).
