# Examples repository

Runnable game examples are maintained in the sibling `Valthorne-examples`
repository, conventionally checked out at `../Valthorne-examples`.
Make example changes there, preserving its packages, documentation and formatting.
Do not add or update runnable examples under this repository's `src/examples`.
Existing legacy copies are not the source of truth.

Engine implementations, unit tests and portable compatibility fixtures belong
in Valthorne. The web FPS exporter reads the separate examples repository.
Use the examples project's `-PvalthorneDir=../Valthorne` option when testing
unpublished engine API changes.

# Website repository

The website is maintained in the sibling `Valthorne-website` repository at
`../Valthorne-website`, published at
https://github.com/tehnewb/Valthorne-website. Make website changes there, not in
Valthorne. Its GitHub Pages workflow owns the live site deployment.
