# Update Message Encoding/Decoding for Transport Layer

This plan updates `MessageEncoder` and `MessageDecoder` to use a `ByteArray` based API and compact JSON serialization. It also includes a unit test to verify the implementation, specifically handling Devanagari (Hindi) text.

## Proposed Changes

### Core Transport Components

#### [MODIFY] [MessageEncoder.kt](file:///D:/Projects/iTantra-Android/app/src/main/java/com/itantra/app/transport/MessageEncoder.kt)
- Update signature to `encode(message: Message): ByteArray`.
- Implement compact JSON serialization using `org.json.JSONObject`.
- Map fields to short names: `id` -> `i`, `sender` -> `s`, `language` -> `l`, `text` -> `t`, `timestamp` -> `ts`.
- Ensure UTF-8 encoding.

#### [MODIFY] [MessageDecoder.kt](file:///D:/Projects/iTantra-Android/app/src/main/java/com/itantra/app/transport/MessageDecoder.kt)
- Update signature to `decode(data: ByteArray): Result<Message>`.
- Implement JSON parsing with `org.json.JSONObject`.
- Use `runCatching` to return `Result.failure` on malformed input or missing fields.
- Validate required fields before constructing the `Message` object.

### Testing

#### [NEW] [MessageCoderTest.kt](file:///D:/Projects/iTantra-Android/app/src/test/java/com/itantra/app/transport/MessageCoderTest.kt)
- Unit test to verify round-trip serialization/deserialization.
- Test case with Hindi text: `language = Language.HINDI`, `text = "मुझे मदद चाहिए"`.

## Verification Plan

### Automated Tests
- Run the new unit test: `./gradlew :app:testDebugUnitTest --tests "com.itantra.app.transport.MessageCoderTest"`

### Manual Verification
- None required as these are pure logic components.

> [!NOTE]
> `org.json` is part of the Android SDK. In local JUnit tests, it is typically stubbed. To make the unit test pass without a device/emulator, I may need to add `testImplementation("org.json:json:20240303")` to `app/build.gradle.kts`. I will attempt to run the test first and only suggest this change if it fails with stub errors.
