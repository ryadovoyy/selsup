# selsup

This project implements the `CrptApi` class for interacting with the Honest Mark API. The class is designed to be thread-safe and allows for limiting the number of requests made to the API within a specified time interval.

## Constraints

- Java 17 or older
- The limit on the number of requests in a certain time interval must be specified in the constructor
- If the limit is exceeded, the request call should be blocked and continue execution without throwing an exception
- All additional classes must be inner classes
- It is allowed to use HTTP client and JSON serialization libraries

## Implementation details

The `createDocument` method is implemented to create a document (e.g. for entering goods into circulation). The document and signature are passed to the method as an object and string respectively.

The `POST` request is sent to the following URL: `https://ismp.crpt.ru/api/v3/lk/documents/create`. In the request body, the document is passed in JSON format:

```json
{
  "description": {
    "participantInn": "string"
  },
  "doc_id": "string",
  "doc_status": "string",
  "doc_type": "LP_INTRODUCE_GOODS",
  "importRequest": true,
  "owner_inn": "string",
  "participant_inn": "string",
  "producer_inn": "string",
  "production_date": "2020-01-23",
  "production_type": "string",
  "products": [
    {
      "certificate_document": "string",
      "certificate_document_date": "2020-01-23",
      "certificate_document_number": "string",
      "owner_inn": "string",
      "producer_inn": "string",
      "production_date": "2020-01-23",
      "tnved_code": "string",
      "uit_code": "string",
      "uitu_code": "string"
    }
  ],
  "reg_date": "2020-01-23",
  "reg_number": "string"
}
```

## Usage

An example of use is shown in the `Main.java` file.
