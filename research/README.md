# Adult-site research dataset pipeline

This branch is used to create a research-only dataset of approximately 1,000 currently reachable adult-content domains.

Method:
- Cross-source classification using OISD NSFW, StevenBlack porn-only, Block List Project porn, and hostsVN Adult.
- Excludes every domain in the two wpzzz/blocked-sites-in-south-korea lists supplied by the researcher.
- Lightweight homepage verification; no media is downloaded.
- Rejects parking/domain-sale pages and conservative exploitative/illegal-theme keyword matches.
- Country/region is inferred from ccTLD, regional signals, or page language; it is not server location or legal domicile.
