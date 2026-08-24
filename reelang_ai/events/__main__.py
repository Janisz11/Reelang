import asyncio

from ..db_log_handler import install_database_log_handler
from .consumer import main

if __name__ == "__main__":
    install_database_log_handler()
    asyncio.run(main())
