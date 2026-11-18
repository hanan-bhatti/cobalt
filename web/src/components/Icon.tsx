export function Icon({ name, size = 20 }: {
    name: string;
    size?: number;
}) { return <img className="icon" src={'/workspace/icons/' + name + '.svg'} width={size} height={size} alt="" aria-hidden="true"/>; }
