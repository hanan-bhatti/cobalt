with open("app/src/foss/kotlin/com/example/data/repository/CobaltRepository.kt", "r") as f:
    content = f.read()

old_toDomain = """            releaseYear = releaseYear,
            rating = rating
        )
    }"""
new_toDomain = """            releaseYear = releaseYear,
            rating = rating,
            isArchived = isArchived
        )
    }"""
content = content.replace(old_toDomain, new_toDomain)

old_toEntity = """            releaseYear = releaseYear,
            rating = rating
        )
    }"""
new_toEntity = """            releaseYear = releaseYear,
            rating = rating,
            isArchived = isArchived
        )
    }"""
content = content.replace(old_toEntity, new_toEntity)

with open("app/src/foss/kotlin/com/example/data/repository/CobaltRepository.kt", "w") as f:
    f.write(content)
